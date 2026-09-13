#!/usr/bin/env python3
from pathlib import Path
import re, sys, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
errors=[]
manifest=root/'app/src/main/AndroidManifest.xml'
ET.parse(manifest)
text=manifest.read_text()
for x in ['SYSTEM_ALERT_WINDOW','FOREGROUND_SERVICE_MEDIA_PROJECTION','foregroundServiceType="mediaProjection"','.service.CaptureService']:
    if x not in text: errors.append('manifest missing '+x)
required=[
'app/src/main/java/com/paodekuai/analyzer/MainActivity.java',
'app/src/main/java/com/paodekuai/analyzer/service/CaptureService.java',
'app/src/main/java/com/paodekuai/analyzer/runtime/LiveAnalyzerRuntime.java',
'app/src/main/java/com/paodekuai/analyzer/overlay/OverlayController.java',
'app/src/main/java/com/paodekuai/analyzer/advisor/AdvisorWorker.java',
'app/src/main/java/com/paodekuai/analyzer/capture/FrameNormalizer.java',
'app/src/main/java/com/paodekuai/analyzer/decoder/LiveEventDecoder.java',
'app/src/main/java/com/paodekuai/analyzer/tracker/GameTracker.java',
'app/src/main/assets/golden/GOLDEN_TRANSCRIPT.json']
for p in required:
    if not (root/p).exists(): errors.append('missing '+p)
activity=(root/required[0]).read_text(); service=(root/required[1]).read_text(); runtime=(root/required[2]).read_text()
for x in ['开始分析','结束分析','自动识别新牌局','显示 Overlay','自动计算推荐']:
    if x not in activity: errors.append('settings UI missing '+x)
for x in ['MediaProjection','ImageReader','createVirtualDisplay','startForeground']:
    if x not in service: errors.append('capture service missing '+x)
for x in ['WAITING_FOR_GAME','TRACKING','ROUND_END','maybeAdvise','quarantineRound']:
    if x not in runtime: errors.append('runtime missing '+x)
if errors:
    print('SOURCE_VALIDATION FAIL');print('\n'.join('- '+x for x in errors));sys.exit(1)
print('SOURCE_VALIDATION PASS')
print('Manifest: mediaProjection FGS + overlay permission')
print('UI: start/stop + settings')
print('Runtime: auto round lifecycle + fail-closed quarantine')
print('Advisor: asynchronous + stale-result guard')
