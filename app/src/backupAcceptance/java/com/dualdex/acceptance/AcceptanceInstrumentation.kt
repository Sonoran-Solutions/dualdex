package com.dualdex.acceptance

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.ParcelFileDescriptor
import android.app.backup.FullBackupDataOutput
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.app.AppCompatActivity
import com.dualdex.BuildConfig
import com.dualdex.companion.CompanionViewModel
import com.dualdex.companion.ui.SaveStateScreenView
import com.dualdex.emulator.*
import com.dualdex.emulator.backup.*
import com.dualdex.emulator.backup.StagedRestoreStore.Decision.*
import com.dualdex.emulator.backup.StagedRestoreStore.Result.*
import com.dualdex.emulator.storage.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.security.MessageDigest
import java.util.UUID

private const val PACKAGE = "com.dualdex.backupacceptance"
private val HASH = "1".repeat(64)
private val HASH2 = "2".repeat(64)
private val ROM = RomIdentity.create(HASH, "Synthetic One")
private val OLD = ByteArray(131072) { 3 }
private val NEW = ByteArray(131072) { 9 }
private val STATE = ByteArray(262144) { 5 }
private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
private fun put(root: File, path: String, bytes: ByteArray, time: Long = System.currentTimeMillis()) = File(root, path).apply {
    check(parentFile!!.isDirectory || parentFile!!.mkdirs()); writeBytes(bytes); check(setLastModified(time))
}
private fun battery(hash: String = HASH) = "saves_v2/$hash/battery.sav"

/** Test-only logs; no production paths or personal game data are inspected. */
class ObservedBackupAgent : QuotaAwareBackupAgent() {
    override fun onCreate() { super.onCreate(); Log.i("DualDexAcceptance", "callback onCreate") }
    override fun onFullBackup(data: FullBackupDataOutput) {
        Log.i("DualDexAcceptance", "callback onFullBackup flags=${data.transportFlags} quota=${data.quota}")
        try { super.onFullBackup(data); Log.i("DualDexAcceptance", "callback onFullBackup returned") }
        catch (e: Exception) { Log.i("DualDexAcceptance", "callback onFullBackup aborted ${e.javaClass.simpleName}: ${e.cause?.message}"); throw e }
    }
    override fun onQuotaExceeded(bytes: Long, quota: Long) {
        Log.i("DualDexAcceptance", "callback onQuotaExceeded bytes=$bytes quota=$quota"); super.onQuotaExceeded(bytes, quota)
    }
    override fun onRestoreFile(data: ParcelFileDescriptor, size: Long, destination: File, type: Int, mode: Long, mtime: Long) {
        Log.i("DualDexAcceptance", "callback onRestoreFile type=$type size=$size name=${destination.name}")
        super.onRestoreFile(data, size, destination, type, mode, mtime)
    }
    override fun onRestoreFinished() { Log.i("DualDexAcceptance", "callback onRestoreFinished"); super.onRestoreFinished() }
}

/** Hosts the production Saves view without any ROM, emulator or owner's settings. */
class AcceptanceActivity : AppCompatActivity() {
    companion object { @Volatile var current: AcceptanceActivity? = null }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == PACKAGE)
        current = this
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(SaveStateScreenView(this, CompanionViewModel()))
    }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
}

/** ROM-free bridge. Device storage is real; these core calls are explicitly simulated. */
private class SyntheticCore : LibretroCoreBridge {
    var sram = OLD.copyOf()
    var loads = 0
    var rejectState = false
    override fun flushSaveRam(savePath: String): Boolean { File(savePath).writeBytes(sram); return true }
    override fun loadSaveRam(savePath: String): Boolean { loads++; sram = File(savePath).readBytes(); return true }
    override fun saveState(statePath: String): Boolean { File(statePath).writeBytes(STATE); return true }
    override fun loadState(statePath: String): Boolean = !rejectState && File(statePath).length() == STATE.size.toLong()
}

/** Built-in instrumentation, avoiding an extra test dependency or a second APK/package. */
class AcceptanceInstrumentation : Instrumentation() {
    private var arguments = Bundle()
    private val results = JSONArray()
    override fun onCreate(arguments: Bundle?) { this.arguments = arguments ?: Bundle(); start() }
    override fun onStart() {
        try {
            check(targetContext.packageName == PACKAGE && BuildConfig.DEBUG && BuildConfig.FLAVOR == "backupAcceptance")
            when (arguments.getString("mode", "storage")) {
                "storage" -> storage()
                "staged" -> staged()
                "seed" -> seed()
                "inspect" -> inspect()
                "cloud" -> cloud()
                "ui" -> ui()
                "restart" -> restart()
                else -> error("Unknown acceptance mode")
            }
        } catch (e: Throwable) { record("runner", "android", e) }
        val mode = arguments.getString("mode", "storage")
        val report = JSONObject().put("package", PACKAGE).put("api", Build.VERSION.SDK_INT)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("build", Build.FINGERPRINT)
            .put("mode", mode).put("results", results)
        val failed = (0 until results.length()).any { results.getJSONObject(it).getString("status") == "FAIL" }
        val out = File(targetContext.filesDir, "acceptance/report-$mode.json").apply { parentFile!!.mkdirs(); writeText(report.toString(2)) }
        Log.i("DualDexAcceptance", "report $mode ${if (failed) "FAIL" else "PASS"}")
        finish(if (failed) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            Bundle().apply { putString("acceptance", if (failed) "FAIL" else "PASS"); putString("report", out.name) })
    }
    private fun record(name: String, kind: String, failure: Throwable? = null, evidence: JSONObject = JSONObject()) {
        results.put(JSONObject().put("name", name).put("kind", kind).put("status", if (failure == null) "PASS" else "FAIL")
            .put("evidence", evidence).also { if (failure != null) it.put("error", "${failure.javaClass.name}: ${failure.message}").put("stack", failure.stackTrace.take(6).joinToString("\n")) })
        Log.i("DualDexAcceptance", "$name ${if (failure == null) "PASS" else "FAIL: $failure"}")
    }
    private fun test(name: String, kind: String = "android-storage", block: (File) -> JSONObject) {
        val root = File(targetContext.filesDir, "acceptance/work/$name").apply { deleteRecursively(); mkdirs() }
        try { record(name, kind, evidence = block(root)) } catch (e: Throwable) { record(name, kind, e) }
        finally { AtomicSaveFile.syncHook = null; AtomicSaveFile.directorySyncHook = null; AtomicSaveFile.backupHook = null }
    }
    private fun hashes(target: File, previous: File? = null) = JSONObject().put("target", if (target.isFile) digest(target.readBytes()) else "missing")
        .put("previousGood", previous?.takeIf { it.isFile }?.let { digest(it.readBytes()) } ?: "missing")
    private fun storage() {
        test("timestamp-precision") { root ->
            val f=put(root,"mtime",byteArrayOf(1),1700000000123L)
            JSONObject().put("requested",1700000000123L).put("fileLastModified",f.lastModified())
                .put("nioLastModified",java.nio.file.Files.getLastModifiedTime(f.toPath()).toMillis())
        }
        test("directory-channel-sync") { root -> AtomicSaveFile.syncDirectory(root); JSONObject().put("directorySync", "returned normally") }
        test("copy-atomic-replace-and-backup") { root ->
            val target = put(root, "target", OLD); val source = put(root, "staged", NEW)
            check(AtomicSaveFile.copyFromStaging(source, target)); check(target.readBytes().contentEquals(NEW))
            val bak = File(root, "target.bak"); check(bak.readBytes().contentEquals(OLD)); hashes(target, bak)
        }
        test("file-fsync-rejection", "injected-android-storage") { root ->
            val target = put(root, "target", OLD); val source = put(root, "staged", NEW)
            AtomicSaveFile.syncHook = { throw IOException("injected file sync") }
            check(!AtomicSaveFile.copyFromStaging(source, target)); check(target.readBytes().contentEquals(OLD)); hashes(target)
        }
        test("post-rename-directory-sync-rejection", "injected-android-storage") { root ->
            val target = put(root, "target", OLD); val source = put(root, "staged", NEW)
            AtomicSaveFile.directorySyncHook = { check(target.readBytes().contentEquals(NEW)); throw IOException("injected directory sync") }
            check(!AtomicSaveFile.copyFromStaging(source, target)); val bak = File(root, "target.bak")
            check(target.readBytes().contentEquals(NEW)); check(bak.readBytes().contentEquals(OLD)); hashes(target, bak)
        }
        test("missing-invalid-recovery") { root ->
            val target = File(root, "target"); check(!AtomicSaveFile.recoverInterrupted(target, OLD.size.toLong()))
            put(root, "target.bak", byteArrayOf()); check(!AtomicSaveFile.recoverInterrupted(target, OLD.size.toLong()))
            put(root, "target.bak", OLD); check(AtomicSaveFile.recoverInterrupted(target, OLD.size.toLong())); check(target.readBytes().contentEquals(OLD)); hashes(target)
        }
        for (existing in listOf(false, true)) test("manual-import-$existing", "fake-core-android-storage") { root ->
            val core = SyntheticCore(); val manager = SaveStateManager(customBaseDir = root, coreBridge = core).apply { setActiveGame(ROM) }
            val target = manager.getCanonicalFile(ROM, SaveStateFiles.BATTERY)
            if (existing) target.writeBytes(OLD)
            val future = System.currentTimeMillis() + 7 * 86400000L
            put(root, "saves_v2/$HASH/auto_resume.state", STATE, future)
            put(root, "saves_v2/$HASH/slot_1.state", STATE, future)
            check(manager.importBatterySave(ROM, NEW.inputStream())); check(target.readBytes().contentEquals(NEW)); check(core.sram.contentEquals(NEW))
            check(manager.loadNewestResumeState(ROM) == null)
            check(manager.saveAutoResume(ROM)); check(manager.loadNewestResumeState(ROM) == SaveStateFiles.AUTO_RESUME)
            check(manager.quickSave(ROM)); check(manager.quickLoad(ROM)); check(manager.saveSlot(ROM, 2)); check(manager.loadSlot(ROM, 2))
            check(manager.flushBatterySave(ROM) is SaveWriteResult.Success)
            val restart = SaveStateManager(customBaseDir = root, coreBridge = core).apply { setActiveGame(ROM) }
            check(restart.loadNewestResumeState(ROM) != null)
            core.rejectState = true; check(!restart.loadSlot(ROM, 1)); check(target.readBytes().contentEquals(NEW))
            hashes(target).put("newStateMtime", manager.getCanonicalFile(ROM, SaveStateFiles.AUTO_RESUME).lastModified())
        }
        for (existing in listOf(false, true)) for (directory in listOf(false, true)) test("guard-failure-$existing-$directory", "injected-fake-core-android-storage") { root ->
            val core = SyntheticCore(); val manager = SaveStateManager(customBaseDir = root, coreBridge = core).apply { setActiveGame(ROM) }
            val target = manager.getCanonicalFile(ROM, SaveStateFiles.BATTERY); if (existing) target.writeBytes(OLD)
            val dir = manager.getCanonicalRomDir(ROM); val marker = File(dir, SaveStateFiles.RESUME_BARRIER)
            val resume = put(dir, SaveStateFiles.AUTO_RESUME, STATE, System.currentTimeMillis() + 7 * 86400000L)
            check(!marker.exists()); var injected = false
            if (directory) AtomicSaveFile.directorySyncHook = { injected = true; check(marker.exists()); throw IOException("injected guard directory sync") }
            else AtomicSaveFile.syncHook = { stream ->
                if (File(dir, "${SaveStateFiles.RESUME_BARRIER}.tmp").exists()) { injected = true; throw IOException("injected guard file sync") }; stream.fd.sync()
            }
            check(!manager.importBatterySave(ROM, NEW.inputStream())); check(injected); check(core.loads == 0); check(core.sram.contentEquals(OLD))
            check(target.exists() == existing); if (existing) check(target.readBytes().contentEquals(OLD)); check(resume.readBytes().contentEquals(STATE))
            hashes(target).put("candidateLoads", core.loads).put("markerPresent", marker.exists()).put("resume", digest(resume.readBytes()))
        }
    }
    private fun ready(root: File, hash: String = HASH, bytes: ByteArray = NEW): File {
        val session = File(root, "restore_staging/session-${UUID.randomUUID()}")
        put(session, battery(hash), bytes, 1000)
        return StagedRestoreStore(root).complete(session)
    }
    private fun review(root: File, hash: String = HASH) = StagedRestoreStore(root).review().filterNot { it.retainedLocal }.flatMap { it.entries }.single { it.source.path == battery(hash) }
    private fun staged() {
        test("staged-publication-and-conflicts") { root ->
            val ready = ready(root); val store = StagedRestoreStore(root); val target = File(root, battery())
            check(!target.exists()); check(store.import(review(root), KEEP_LOCAL) == KEPT_LOCAL); check(!target.exists())
            val missing = review(root); put(root, battery(), OLD, 2000)
            check(store.import(missing, IMPORT_MISSING) == REVIEW_AGAIN)
            val newer = review(root); check(store.import(newer, IMPORT_MISSING) == REVIEW_AGAIN)
            check(store.import(newer, KEEP_LOCAL) == KEPT_LOCAL); check(target.readBytes().contentEquals(OLD))
            target.writeBytes(ByteArray(OLD.size) { 7 }); check(target.setLastModified(newer.local!!.modified))
            check(store.import(newer, REPLACE_LOCAL) == REVIEW_AGAIN)
            target.writeBytes(byteArrayOf()); val empty = review(root); check(empty.local != null)
            check(store.import(empty, IMPORT_MISSING) == REVIEW_AGAIN)
            target.writeBytes(OLD); val bak = put(root, battery()+".bak", byteArrayOf(8))
            check(store.import(review(root), REPLACE_LOCAL) == IMPORTED)
            check(target.readBytes().contentEquals(NEW)); check(bak.readBytes().contentEquals(byteArrayOf(8)))
            check(File(ready, battery()).readBytes().contentEquals(NEW))
            check(StagedRestoreStore(root).review().filter { it.retainedLocal }.flatMap { it.entries }.any { it.source.version.sha256 == digest(OLD) })
            hashes(target,bak).put("retainedLocal", digest(OLD))
        }
        test("invalid-and-incomplete-isolation") { root ->
            val store = StagedRestoreStore(root); val pending = File(root, "restore_staging/session-invalid")
            put(pending,battery(),NEW); put(pending,"saves_v2/$HASH/metadata.json","{".toByteArray())
            check(runCatching { store.complete(pending) }.isFailure); check(store.review().isEmpty()); check(!File(root,battery()).exists()); JSONObject()
        }
        test("loaded-game-refusal", "fake-core-android-storage") { root ->
            ready(root); val manager = SaveStateManager(customBaseDir=root,coreBridge=SyntheticCore()).apply { setActiveGame(ROM) }
            check(manager.importRestoredSave(review(root),IMPORT_MISSING)==CLOSE_GAME); check(!File(root,battery()).exists()); JSONObject()
        }
        test("strict-atomic-rename-rejection", "injected-android-storage") { root ->
            val source = put(root,"source",NEW); val target = put(root,"target",OLD); val retained=File(root,"retained")
            val sourceVersion=checkNotNull(AtomicSaveFile.version(source,NEW.size.toLong())); val local=checkNotNull(AtomicSaveFile.version(target,OLD.size.toLong()))
            check(runCatching { AtomicSaveFile.commitRestore(source,target,sourceVersion,local,retained,{}, move={ _,_ -> throw AtomicMoveNotSupportedException("injected","target","controlled rejection") }) }.isFailure)
            check(target.readBytes().contentEquals(OLD)); check(retained.readBytes().contentEquals(OLD)); hashes(target,retained)
        }
    }
    private fun seed() {
        check(targetContext.packageName==PACKAGE)
        val root=targetContext.filesDir
        for(name in listOf("saves_v2","saves","saves_fallback","cloud_backup","restore_staging")) File(root,name).deleteRecursively()
        for(hash in listOf(HASH,HASH2)) {
            put(root,battery(hash), if(hash==HASH) OLD else NEW)
            put(root,"saves_v2/$hash/metadata.json",RomSaveMetadata(hash,"Synthetic ${if(hash==HASH) "One" else "Two"}").toJson().toByteArray())
            put(root,"saves_v2/$hash/slot_1.state",STATE)
            put(root,"saves_v2/$hash/slot_1.png",byteArrayOf(1,2,3))
        }
        for(path in listOf(battery()+".bak",battery()+".tmp")) put(root,path,OLD)
        if(arguments.getString("scenario")=="full-tree") for(path in listOf("saves/legacy.gba.sav","saves/legacy.gba.sav.orig.bak","saves_fallback/recovery.state")) put(root,path,OLD)
        targetContext.getSharedPreferences("dualdex_legacy_catalog",0).edit().putString("acceptance","synthetic").commit()
        put(root,"rom_cache/excluded.gba",byteArrayOf(10)); put(root,"debug/excluded.log",byteArrayOf(11))
        targetContext.getSharedPreferences("dualdex_settings",0).edit().putString("acceptance-excluded","synthetic").commit()
        if(arguments.getString("scenario")=="malformed") put(root,"saves_v2/$HASH/metadata.json","{".toByteArray())
        val admitted=JSONArray()
        val catalog=File(File(root.parentFile,"shared_prefs"),"dualdex_legacy_catalog.xml")
        BackupFileTreePolicy.collect(root,catalog).forEach { file -> admitted.put(JSONObject().put("path",file.relativePath).put("sha256",digest(file.file.readBytes()))) }
        record("seed-synthetic-only","fixture", evidence=JSONObject().put("batteryOne",digest(OLD)).put("batteryTwo",digest(NEW)).put("admitted",admitted))
    }
    private fun inspect() {
        val root=targetContext.filesDir; val store=StagedRestoreStore(root); val sessions=store.review()
        val entries=JSONArray()
        sessions.flatMap { it.entries }.forEach { e -> entries.put(JSONObject().put("path",e.source.path).put("sha256",e.source.version.sha256).put("target",e.source.target).put("romHash",e.source.romHash ?: JSONObject.NULL)) }
        record("inspect-staged-sessions","android-storage",evidence=JSONObject().put("sessions",sessions.size).put("entries",entries)
            .put("canonicalBattery",File(root,battery()).takeIf { it.isFile }?.let { digest(it.readBytes()) } ?: "missing"))
    }
    private fun cloud() {
        test("cloud-bounded-selection","in-process-android-agent-helper") { root ->
            for(hash in listOf(HASH,HASH2)) { put(root,battery(hash),OLD); put(root,"saves_v2/$hash/metadata.json",RomSaveMetadata(hash,"Synthetic").toJson().toByteArray()); put(root,"saves_v2/$hash/slot_1.state",STATE) }
            put(root,battery()+".bak",ByteArray(24*1024*1024){4})
            val inventory=SaveBackupInventory(root).scan()
            for(stamp in inventory.sourceStamps.values) {
                val file=File(root,stamp.relativePath)
                check(file.lastModified()==stamp.modifiedAtMillis) { "Timestamp API mismatch: File=${file.lastModified()} NIO=${stamp.modifiedAtMillis}" }
            }
            val cache=File(root,"cache").apply { mkdirs() }; var emitted:File?=null
            val verdict=QuotaAwareBackupAgent.emitCloudSnapshot(root,cache,700000,emptyMap()) { emitted=it }
            check(verdict==QuotaBackupSelection.Verdict.PARTIAL); val archive=checkNotNull(emitted)
            check(archive.length()<=700000); check(CloudSnapshotArchive.validate(archive)!=null)
            val result=JSONObject().put("archiveBytes",archive.length()).put("archiveSha256",digest(archive.readBytes())).put("verdict",verdict.name)
            check(runCatching { QuotaAwareBackupAgent.emitCloudSnapshot(root,cache,100000,emptyMap()) { error("must not emit") } }.isFailure)
            put(root,"saves_v2/$HASH/metadata.json","{".toByteArray())
            check(runCatching { QuotaAwareBackupAgent.emitCloudSnapshot(root,cache,25000000,emptyMap()) { error("must not emit") } }.isFailure)
            result
        }
        test("cloud-mutation-before-publication","injected-android-storage") { root ->
            put(root,battery(),OLD); put(root,"saves_v2/$HASH/metadata.json",RomSaveMetadata(HASH,"Synthetic").toJson().toByteArray())
            val inventory=SaveBackupInventory(root).scan()
            val entries=inventory.candidates.map { c -> CloudSnapshotArchive.Entry(File(root,c.relativePath),checkNotNull(inventory.sourceStamps[c.relativePath]),c.romSha256,c.category.name) }
            val target=put(root,QuotaBackupSelection.CLOUD_SNAPSHOT_PATH,byteArrayOf(12))
            val result=CloudSnapshotArchive.build(entries,root,File(root,"cache"),target) { index ->
                if(index==0) { val f=entries.sortedBy{it.stamp.relativePath}.first().source; val time=f.lastModified(); f.writeBytes(NEW); check(f.setLastModified(time)) }
            }
            check(result==null); check(target.readBytes().contentEquals(byteArrayOf(12))); hashes(target)
        }
        test("cloud-incomplete-inventory-refusal","injected-android-storage") { root ->
            put(root,battery(),OLD); put(root,"saves_v2/$HASH/metadata.json",RomSaveMetadata(HASH,"Synthetic").toJson().toByteArray())
            java.nio.file.Files.createSymbolicLink(File(root,"saves_v2/$HASH/slot_1.state").toPath(),File(root,battery()).toPath())
            check(runCatching { QuotaAwareBackupAgent.emitCloudSnapshot(root,File(root,"cache"),25000000,emptyMap()){error("must not emit")} }.isFailure)
            check(!File(root,QuotaBackupSelection.CLOUD_SNAPSHOT_PATH).exists()); JSONObject()
        }
    }
    private fun restart() {
        test("manager-recreation","fake-core-android-storage") { root ->
            val core=SyntheticCore(); val a=SaveStateManager(customBaseDir=root,coreBridge=core).apply{setActiveGame(ROM)}
            check(a.saveAutoResume(ROM)); val b=SaveStateManager(customBaseDir=root,coreBridge=core).apply{setActiveGame(ROM)}
            check(b.loadNewestResumeState(ROM)==SaveStateFiles.AUTO_RESUME); JSONObject()
        }
        inspect() // Executed in a separate process after am force-stop; completed sessions must remain reviewable.
    }
    private fun node(text:String, exact:Boolean=false):AccessibilityNodeInfo? {
        fun find(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(n==null) return null
            if(n.text?.toString()?.let { if(exact) it.equals(text,true) else it.contains(text,true) }==true) return n
            for(i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        find(uiAutomation.rootInActiveWindow)?.let { return it }
        for(w in uiAutomation.windows) find(w.root)?.let { return it }
        return null
    }
    private fun await(block:()->Boolean) { val until=System.currentTimeMillis()+15000; while(System.currentTimeMillis()<until) { if(block()) return; Thread.sleep(100) }; error("Timed out waiting for UI") }
    private fun click(text:String,exact:Boolean=false) {
        try { await { node(text,exact)!=null } } catch(e:Exception) { error("UI click unavailable: $text; root=${uiAutomation.rootInActiveWindow}") }; var n=node(text,exact)
        while(n!=null && !n.isClickable) n=n.parent
        check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true); waitForIdleSync()
    }
    private fun ui() {
        val root=targetContext.filesDir
        val info=uiAutomation.serviceInfo
        info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        uiAutomation.serviceInfo=info
        File(root,"saves_v2").deleteRecursively(); File(root,"restore_staging").deleteRecursively()
        ready(root); ready(root,HASH2); put(root,battery(),OLD,System.currentTimeMillis()+86400000L); put(root,battery()+".bak",byteArrayOf(8))
        val activity=startActivitySync(Intent(targetContext,AcceptanceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            click("Review restored saves",true); await { node("Restored backup:")!=null }
            click(battery()); await{node("ROM SHA-256: $HASH")!=null}; click("Review replacement…",true)
            await{node("Replace and retain local",true)!=null}; click("Cancel",true)
            check(File(root,battery()).readBytes().contentEquals(OLD)); record("ui-newer-local-cancel","android-ui")
            val target=File(root,battery()); val localTime=target.lastModified()
            target.writeBytes(byteArrayOf())
            click("Review restored saves",true); click(battery()); await{node("Local file: 0 bytes")!=null}
            click("Review replacement…",true); await{node("Replace and retain local",true)!=null}; click("Cancel",true)
            check(target.length()==0L); record("ui-empty-existing-file-is-conflict","android-ui")
            target.writeBytes(OLD); check(target.setLastModified(localTime))
            click("Review restored saves",true); click(battery())
            target.writeBytes(ByteArray(OLD.size){7}); check(target.setLastModified(localTime))
            click("Review replacement…",true); click("Replace and retain local",true)
            await{node("Restored backup:")!=null}; check(target.readBytes().all{it==7.toByte()})
            check(StagedRestoreStore(root).review().none{it.retainedLocal})
            click("Close",true); record("ui-same-size-time-mutation-refused","android-ui")
            target.writeBytes(OLD); check(target.setLastModified(localTime))
            click("Review restored saves",true); click(battery()); click("Review replacement…",true); click("Replace and retain local",true)
            await{File(root,battery()).readBytes().contentEquals(NEW)}
            check(File(root,battery()+".bak").readBytes().contentEquals(byteArrayOf(8)))
            check(StagedRestoreStore(root).review().filter{it.retainedLocal}.flatMap{it.entries}.any{it.source.version.sha256==digest(OLD)})
            record("ui-explicit-replacement-retains-local","android-ui",evidence=hashes(File(root,battery())))
            await{node("Restored backup:")!=null}; click(battery(HASH2)); click("Import…",true)
            put(root,battery(HASH2),OLD)
            click("Import file",true); await{node("Restored backup:")!=null}
            check(File(root,battery(HASH2)).readBytes().contentEquals(OLD)); click("Close",true)
            File(root,battery(HASH2)).delete(); record("ui-late-created-destination-refused","android-ui")
            click("Review restored saves",true); click(battery(HASH2)); click("Import…",true)
            await{node("Import this restored file?",true)!=null}; click("Cancel",true); check(!File(root,battery(HASH2)).exists())
            record("ui-missing-destination-confirmation-cancel","android-ui")
            click("Review restored saves",true); click(battery(HASH2)); click("Import…",true); click("Import file",true)
            await{File(root,battery(HASH2)).isFile}; check(File(root,battery(HASH2)).readBytes().contentEquals(NEW))
            record("ui-missing-destination-confirmed-import","android-ui")
            await{node("Restored backup:")!=null}
            runOnMainSync{activity.recreate()}; await{AcceptanceActivity.current!=null && AcceptanceActivity.current!==activity}
            await{node("Review restored saves",true)!=null}; click("Review restored saves",true); await{node("Retained local copy")!=null}; click("Close",true)
            record("ui-recreate-and-reopen-preserves-sessions","android-ui")
        } catch(e:Throwable) {
            uiAutomation.takeScreenshot()?.let { bitmap ->
                File(root,"acceptance/private-ui-failure.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            }
            throw e
        } finally { runOnMainSync{AcceptanceActivity.current?.finish()} }
    }
}
