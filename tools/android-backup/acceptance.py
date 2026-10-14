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


def require_disposable(serial, qemu, expected, actual, reset=False, allow_reset=False):
    if not (qemu and serial.startswith('emulator-') and expected and expected == actual):
        raise ValueError('Transport experiments require --disposable-avd matching the actual emulator AVD name')
    if reset and not allow_reset:
        raise ValueError('D2D transport initialization requires --allow-transport-reset on the designated disposable AVD')


def backup_result(text):
    package = re.findall(r'^Package ' + re.escape(PACKAGE) + r' with result: (.+)$', text, re.M)
    aggregate = re.findall(r'^Backup finished with result: (.+)$', text, re.M)
    if len(package) != 1 or len(aggregate) != 1:
        raise RuntimeError('Missing or ambiguous package/aggregate backup result')
    return {'package': package[0], 'aggregate': aggregate[0]}


def restore_tokens(text):
    tokens = re.findall(r'^\s*([0-9a-fA-F]+)\s*:', text, re.M)
    if len(tokens) != 1:
        raise RuntimeError('Expected one unambiguous restore token; found ' + str(tokens))
    return tokens[0]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial', required=True)
    p.add_argument('--apk', type=Path, default=REPO/'app/build/outputs/apk/backupAcceptance/debug/app-backupAcceptance-debug.apk')
    p.add_argument('--output', type=Path, required=True, help='Private evidence directory outside the repository')
    p.add_argument('--transport', choices=['none', 'local', 'd2d-test'], default='none')
    p.add_argument('--disposable-avd', help='Explicitly designate this exact emulator AVD as disposable')
    p.add_argument('--allow-transport-reset', action='store_true', help='Permit D2D dataset reset on the designated disposable AVD')
    p.add_argument('--retention-matrix', action='store_true', help='Run only bounded LocalTransport retention controls A-E')
    p.add_argument('--baseline-only', action='store_true', help='Verify a transport round trip without a later failure experiment')
    p.add_argument('--cleanup', action='store_true', help='Uninstall only the synthetic test package after testing')
    a = p.parse_args()
    if a.retention_matrix and (a.transport != 'local' or a.baseline_only):
        p.error('--retention-matrix requires --transport local without --baseline-only')
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
    avd_name = None
    if a.transport != 'none':
        if qemu and a.serial.startswith('emulator-'):
            avd_name = run('emu', 'avd', 'name').splitlines()[0].strip()
        try:
            require_disposable(a.serial, qemu, a.disposable_avd, avd_name,
                               a.transport == 'd2d-test', a.allow_transport_reset)
        except ValueError as e: p.error(str(e))
    if a.retention_matrix and (api!='34' or run('shell','id','-u').strip()!='0'):
        p.error('Retention diagnostics require a fresh designated API 34 AVD with adb root enabled')
    if not qemu and app.get(ns+'allowBackup') != 'false':
        p.error('Personal-device testing requires DUALDEX_ACCEPTANCE_BACKUP=false APK')
    metadata={'api':api,'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
              'sourceHead':subprocess.check_output(['git','rev-parse','HEAD'],cwd=REPO,text=True).strip(),
              'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=REPO,text=True).strip()),
              'transportMode':a.transport,'emulator':qemu,'disposableAvd':avd_name,
              'fingerprint':run('shell','getprop','ro.build.fingerprint').strip()}
    (out/'environment.json').write_text(json.dumps(metadata,indent=2))
    run('install','-r','-t',str(apk))
    try:
        for mode in ([] if a.retention_matrix else ['storage','staged','cloud','ui']):
            print(f'API {api}: {mode}',flush=True)
            if mode=='ui': run('shell','input','keyevent','KEYCODE_WAKEUP')
            instrument(mode)
        if not a.retention_matrix:
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
            def capture_transport(label):
                current=run('shell','bmgr','list','transports')
                assert re.search(r'^\s*\*\s+'+re.escape(transport)+r'\s*$',current,re.M), 'Unexpected selected transport'
                sets=run('shell','bmgr','list','sets')
                (out/(label+'-transport.json')).write_text(json.dumps({'selected':transport,'list':current,'sets':sets},indent=2))
                return restore_tokens(sets)
            def archive_probe(label):
                archive='/data/user/0/com.android.localtransport/files/1/_full/'+PACKAGE
                size=run('shell','stat','-c','%s',archive,allow_failure=True)
                if 'No such file' in size:
                    state={'exists':False}
                else:
                    state={'exists':True,'size':int(size.strip()),'sha256':run('shell','sha256sum',archive).split()[0]}
                (out/(label+'-archive.json')).write_text(json.dumps(state,indent=2))
            def backup(label):
                text=run('shell','bmgr','backupnow',PACKAGE,timeout=180)
                (out/(label+'-backup.txt')).write_text(text)
                if a.retention_matrix: archive_probe(label)
                result=backup_result(text)
                (out/(label+'-result.json')).write_text(json.dumps(result,indent=2))
                return result
            def restore(label, clear=True):
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
                    if clear: run('shell','pm','clear',PACKAGE)
                    else: run('shell','am','force-stop',PACKAGE)
                    run('shell','am','instrument','-w','-e','mode','inspect',INSTRUMENT)
                    result=run('shell','bmgr','restore',token,PACKAGE,timeout=180)
                (out/(label+'-restore.txt')).write_text(result)
                if 'restoreFinished: 0' not in result: raise RuntimeError('Restore did not finish successfully: '+result)
                report=inspect(label)
                if a.retention_matrix: archive_probe(label)
                return entries(report)
            if a.retention_matrix:
                assert transport=='com.android.localtransport/.LocalTransport'
                results={}
                def expected_seed(scenario=None):
                    report=instrument('seed',scenario)
                    return {e['path']:e['sha256'] for r in report['results'] for e in r['evidence']['admitted']
                            if e['path'].startswith('saves_v2/') and not e['path'].endswith(('.bak','.tmp'))}
                expected=expected_seed()
                assert len(expected)==8
                assert backup('A-valid')['package']=='Success'
                token=capture_transport('A-valid')
                first=restore('A-first'); assert first==expected
                second=restore('A-second'); assert second==expected
                run('shell','am','force-stop',PACKAGE)
                assert entries(inspect('E-process-restart'))==expected
                without_clear=restore('E-without-explicit-clear',clear=False); assert without_clear==expected
                results['A-repeated-restore']={'status':'PASS','first':first,'second':second,'token':token}
                results['E-lifecycle']={'status':'PASS','withoutExplicitClear':without_clear,'token':token}
                newer=expected_seed('new-valid')
                assert newer!=expected and backup('B-valid')['package']=='Success'
                next_token=capture_transport('B-valid'); assert next_token==token
                newest=restore('B-same-token'); assert newest==newer
                results['B-successful-replacement']={'status':'PASS','oldVersionRecoverable':newest==expected,'hashes':newest,'token':next_token}
                expected=expected_seed()
                assert backup('C-baseline')['package']=='Success'
                assert capture_transport('C-baseline')==token
                # No restore of C's baseline before refusal: removes restore-as-cause ambiguity.
                instrument('seed','malformed')
                refused=backup('C-refused'); assert refused['package']!='Success'
                assert capture_transport('C-refused')==token
                after=restore('C-after-refusal')
                results['C-before-emission']={'retention':'PASS' if after==expected else 'FAIL','expected':expected,'recovered':after,'backupResult':refused,'token':token}
                expected=expected_seed()
                assert backup('D-baseline')['package']=='Success'
                assert capture_transport('D-baseline')==token
                assert restore('D-baseline')==expected
                expected_seed('emission-failure')
                emitted=backup('D-emission-failure')
                assert capture_transport('D-emission-failure')==token
                after=restore('D-after-emission-failure')
                results['D-after-emission']={'retention':'PASS' if after==expected else 'FAIL','expected':expected,'recovered':after,'backupResult':emitted,'token':token,'fault':'test-only truncated archive emitted then IOException'}
                log=run('shell','logcat','-d','-s','DualDexAcceptance:I','*:S')
                assert 'injected failure after archive prefix emission' in log, 'Emission fault did not execute'
                (out/'retention-matrix.json').write_text(json.dumps({'transport':transport,'controls':results,'remoteCloudRetention':'NOT_RUN'},indent=2))
                assert all(results[k]['retention']=='PASS' for k in ['C-before-emission','D-after-emission']), 'LocalTransport retention FAIL; see retention-matrix.json'
                return
            seeded=instrument('seed','full-tree' if a.transport=='d2d-test' else None)
            assert backup('valid')['package']=='Success', 'Transport did not accept valid backup'
            token=capture_transport('baseline')
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
            run('shell','logcat','-d','-s','DualDexAcceptance:I','QuotaBackupAgent:W','BackupManagerService:I','PFTBT:I','PerformUnifiedRestoreTask:I','LocalTransport:V','BackupAgent:V','*:'+ 'S',allow_failure=True)
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
