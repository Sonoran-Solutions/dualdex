#!/usr/bin/env python3
"""ROM-free structural release proof; run in canonical CI without release signing.

Android's default source-set contract compiles main + release (never debug). Pin
that contract, the entire no-op factory body, the shared observer API, and absence
of debug storage/export classes or provider in main/release. Debug unit tests also
execute the shared NoOp. No release signing configuration is weakened.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]

def code(path):
    text = path.read_text()
    text = re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
    return re.sub(r'\s+', '', text)

factory = root / 'app/src/release/java/com/dualdex/coverage/HnsCoverageFactory.kt'
expected = '''package com.dualdex.coverage
import android.content.Context
import android.widget.LinearLayout
object HnsCoverageFactory {
 fun create(): HnsCalcCoverageLogger = NoOpHnsCalcCoverageLogger
 fun initialize(context: Context) = Unit
 fun addSettingsActions(container: LinearLayout) = Unit
}'''
assert code(factory) == re.sub(r'\s+', '', expected), 'release factory must be a literal no-op'
shared = code(root / 'app/src/main/java/com/dualdex/coverage/HnsCalcCoverageLogger.kt')
assert 'val logger:HnsCalcCoverageLogger=HnsCoverageFactory.create()'.replace(' ', '') in shared
assert 'objectNoOpHnsCalcCoverageLogger:HnsCalcCoverageLogger{' in shared
noop = shared.split('objectNoOpHnsCalcCoverageLogger:HnsCalcCoverageLogger{')[1].split('objectHnsCoverage')[0]
expected_noop = """
 override fun battle(active: Boolean) = Unit
 override fun record(move: MoveInfo, defender: ParsedPokemon, profile: RomHackProfile,
                     context: BattleHnsCalculationContext, outcome: CalcRequestOutcome) = Unit
}
"""
assert noop == re.sub(r'\s+', '', expected_noop), 'NoOp must have no initialization or side effects'
assert 'funsessionToken():Long?=null' in shared
assert 'funexport' not in shared and 'funclear' not in shared
for source_set in ('main', 'release'):
    for path in (root / f'app/src/{source_set}').rglob('*'):
        if path.suffix not in ('.kt', '.xml'): continue
        text = path.read_text()
        for forbidden in ('DebugHnsCalcCoverageLogger', 'CoverageRepository', 'AtomicCoverageStore',
                          'hns-coverage-export', 'Export H&S Coverage Log', '.hnscoverage', 'hns_coverage_paths'):
            assert forbidden not in text, f'{path}: debug implementation leaked: {forbidden}'
build = code(root / 'app/build.gradle.kts')
assert 'sourceSets' not in build, 'audit required if default Android source-set isolation changes'
assert 'src/debug' not in build
paths = root / 'app/src/debug/res/xml/hns_coverage_paths.xml'
text = paths.read_text()
assert 'path="hns-coverage-export/"' in text
assert '<root-path' not in text and '<external-path' not in text
print('PASS: release factory is NoOp; no logger store/worker/export/UI/provider in main + release')
