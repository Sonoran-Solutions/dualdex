#!/usr/bin/env python3
"""ROM-free device gate. Backup-setting mutations are restricted to disposable emulators."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

if not __debug__:
    raise SystemExit('Run without Python optimization: APK and result assertions must stay enabled')

PACKAGE = 'com.dualdex.backupacceptance'
INSTRUMENT = PACKAGE + '/com.dualdex.acceptance.AcceptanceInstrumentation'
REPO = Path(__file__).resolve().parents[2]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', required=True)
    p.add_argument('--apk', type=Path, default=REPO/'app/build/outputs/apk/backupAcceptance/debug/app-backupAcceptance-debug.apk')
    p.add_argument('--output', type=Path, required=True, help='Private evidence directory outside the repository')
    p.add_argument('--transport', choices=['none', 'local', 'd2d-test'], default='none')
    p.add_argument('--baseline-only', action='store_true', help='Verify a transport round trip without a later failure experiment')
    p.add_argument('--cleanup', action='store_true', help='Uninstall only the synthetic test package after testing')
    a = p.parse_args()
    out = a.output.resolve()
    if out == REPO or REPO in out.parents:
        p.error('Evidence must remain outside the repository')
    out.mkdir(parents=True, exist_ok=True); out.chmod(0o700)
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home()/'Android/Sdk')))
    adb = str(sdk/'platform-tools/adb')
    apk = a.apk.resolve()
    manifest = subprocess.check_output([str(sdk/'cmdline-tools/latest/bin/apkanalyzer'), 'manifest', 'print', str(apk)], text=True)
    import xml.etree.ElementTree as ET
    ns = '{http://schemas.android.com/apk/res/android}'
    m = ET.fromstring(manifest); app = m.find('application'); ins = m.find('instrumentation')
    assert m.get('package') == PACKAGE, 'Refusing to install any other package'
    assert app is not None and app.get(ns+'testOnly') == 'true'
    assert app.get(ns+'fullBackupOnly') == 'true'
    assert app.get(ns+'backupAgent') == 'com.dualdex.acceptance.ObservedBackupAgent'
    assert ins is not None and ins.get(ns+'targetPackage') == PACKAGE
    (out/'merged-manifest.xml').write_text(manifest)
    commands=[]
    def run(*args, timeout=120, allow_failure=False):
        command=[adb,'-s',a.serial,*args]
        r=subprocess.run(command, text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=timeout)
        commands.append({'args':list(args),'returncode':r.returncode,'output':r.stdout})
        (out/'commands.json').write_text(json.dumps(commands,indent=2))
        if r.returncode and not allow_failure: raise RuntimeError(r.stdout)
        return r.stdout
    def instrument(mode, scenario=None):
        args=['shell','am','instrument','-w','-e','mode',mode]
        if scenario: args+=['-e','scenario',scenario]
        text=run(*args,INSTRUMENT,timeout=180)
        report=run('shell','run-as',PACKAGE,'cat','files/acceptance/report-'+mode+'.json')
        (out/(mode+('-'+scenario if scenario else '')+'.json')).write_text(report)
        if 'acceptance=PASS' not in text: raise RuntimeError('Instrumentation did not pass: '+text+'\n'+report)
        return json.loads(report)
    def inspect(label):
        report=instrument('inspect'); (out/(label+'.json')).write_text(json.dumps(report,indent=2)); return report
    def entries(report):
        return {e['path']:e['sha256'] for r in report['results'] for e in r['evidence'].get('entries',[]) if r['name']=='inspect-staged-sessions'}
    api=run('shell','getprop','ro.build.version.sdk').strip()
    qemu=run('shell','getprop','ro.kernel.qemu').strip()=='1'
    if a.transport != 'none' and not (qemu and a.serial.startswith('emulator-')):
        p.error('Transport experiments require a disposable emulator; personal devices are storage/UI only')
    if not qemu and app.get(ns+'allowBackup') != 'false':
        p.error('Personal-device testing requires DUALDEX_ACCEPTANCE_BACKUP=false APK')
    metadata={'api':api,'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
              'sourceHead':subprocess.check_output(['git','rev-parse','HEAD'],cwd=REPO,text=True).strip(),
              'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=REPO,text=True).strip()),
              'transportMode':a.transport,'emulator':qemu}
    (out/'environment.json').write_text(json.dumps(metadata,indent=2))
    run('install','-r','-t',str(apk))
    try:
        for mode in ['storage','staged','cloud','ui']:
            print(f'API {api}: {mode}',flush=True)
            if mode=='ui': run('shell','input','keyevent','KEYCODE_WAKEUP')
            instrument(mode)
        run('shell','am','force-stop',PACKAGE); instrument('restart')
        if a.transport=='none': return
        transports=run('shell','bmgr','list','transports')
        local='com.android.localtransport/.LocalTransport' if 'com.android.localtransport/.LocalTransport' in transports else 'android/com.android.internal.backup.LocalTransport'
        selected=re.search(r'^\s*\*\s+(\S+)',transports,re.M); old_transport=selected.group(1) if selected else None
        old_params=run('shell','settings','get','secure','backup_local_transport_parameters').strip()
        old_d2d=run('shell','settings','get','secure','backup_enable_d2d_test_mode').strip()
        old_enabled=run('shell','settings','get','secure','backup_enabled').strip()
        try:
            run('shell','bmgr','enable','true')
            transport=local if a.transport=='local' else 'com.google.android.gms/.backup.migrate.service.D2dTransport'
            if a.transport=='d2d-test': run('shell','settings','put','secure','backup_enable_d2d_test_mode','1')
            run('shell','bmgr','transport',transport)
            if a.transport=='d2d-test': run('shell','bmgr','init',transport)
            if a.transport=='local': run('shell','settings','put','secure','backup_local_transport_parameters','is_encrypted=true')
            run('shell','bmgr','run'); time.sleep(3)
            run('shell','logcat','-c')
            seeded=instrument('seed','full-tree' if a.transport=='d2d-test' else None)
            text=run('shell','bmgr','backupnow',PACKAGE,timeout=180)
            (out/'backup-valid.txt').write_text(text)
            if f'Package {PACKAGE} with result: Success' not in text:
                raise RuntimeError('Transport did not accept valid backup; see backup-valid.txt')
            sets=run('shell','bmgr','list','sets')
            token=re.search(r'^\s*([0-9a-fA-F]+)\s*:',sets,re.M)
            if not token: raise RuntimeError('No restorable dataset token: '+sets)
            token=token.group(1)
            def restore(label):
                if a.transport=='d2d-test':
                    run('uninstall',PACKAGE)
                    run('shell','bmgr','transport','com.google.android.gms/.backup.BackupTransportService')
                    run('install','-t',str(apk))
                    for _ in range(30):
                        log=run('shell','logcat','-d','-s','DualDexAcceptance:I','*:S')
                        if 'callback onRestoreFinished' in log: break
                        time.sleep(1)
                    else: raise RuntimeError('D2D test-mode install did not finish restore callbacks')
                    result='restoreFinished: 0 (onRestoreFinished after test-mode reinstall)'
                else:
                    run('shell','pm','clear',PACKAGE)
                    run('shell','am','instrument','-w','-e','mode','inspect',INSTRUMENT)
                    result=run('shell','bmgr','restore',token,PACKAGE,timeout=180)
                (out/(label+'-restore.txt')).write_text(result)
                if 'restoreFinished: 0' not in result: raise RuntimeError('Restore did not finish successfully: '+result)
                report=inspect(label); return entries(report)
            baseline=restore('baseline')
            if a.transport=='d2d-test':
                expected={e['path']:e['sha256'] for r in seeded['results'] for e in r['evidence'].get('admitted',[])}
                assert baseline==expected, 'D2D test transport did not restore the complete admitted file set'
            if not baseline: raise RuntimeError('Restore returned no validated staged entries')
            old_sha=hashlib.sha256(bytes([3])*131072).hexdigest()
            assert baseline.get('saves_v2/'+('1'*64)+'/battery.sav')==old_sha, 'Synthetic golden battery did not survive transport'
            if a.baseline_only:
                (out/'transport-result.json').write_text(json.dumps({'transport':transport,'baselineHashes':baseline,'baselineStatus':'PASS','previousDatasetRetention':'NOT_RUN','remoteCloudPreservation':'NOT_RUN','realTwoDeviceTransfer':'NOT_RUN'},indent=2))
                return
            instrument('seed','malformed')
            failed=run('shell','bmgr','backupnow',PACKAGE,timeout=180)
            (out/'backup-refused.txt').write_text(failed)
            if f'Package {PACKAGE} with result: Success' in failed: raise RuntimeError('Malformed metadata unexpectedly backed up successfully')
            recovered=restore('after-refusal')
            (out/'transport-result.json').write_text(json.dumps({'transport':transport,'baselineHashes':baseline,'afterRefusalHashes':recovered,
                'previousDatasetRetention':'PASS' if recovered==baseline else 'FAIL','localOrSingleDeviceOnly':True,'remoteCloudPreservation':'NOT_RUN','realTwoDeviceTransfer':'NOT_RUN'},indent=2))
            assert recovered==baseline, 'Earlier local/test-transport dataset changed after refusal'
            print('Transport round-trip and earlier test dataset retention passed; remote cloud and real D2D NOT_RUN',flush=True)
        finally:
            run('shell','logcat','-d','-s','DualDexAcceptance:I','QuotaBackupAgent:W','BackupManagerService:I','PFTBT:I','PerformUnifiedRestoreTask:I','*:'+ 'S',allow_failure=True)
            (out/'callbacks.log').write_text(commands[-1]['output'])
            if old_transport: run('shell','bmgr','transport',old_transport,allow_failure=True)
            if old_enabled=='0': run('shell','bmgr','enable','false',allow_failure=True)
            for key,value in [('backup_local_transport_parameters',old_params),('backup_enable_d2d_test_mode',old_d2d)]:
                run('shell','settings', 'delete' if value=='null' else 'put','secure',key,*([] if value=='null' else [value]),allow_failure=True)
    finally:
        if a.cleanup: run('uninstall',PACKAGE,allow_failure=True)

if __name__=='__main__':
    try: main()
    except (AssertionError,RuntimeError,subprocess.SubprocessError) as e:
        print(str(e),file=sys.stderr); sys.exit(1)
