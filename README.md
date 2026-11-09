# 보이는 소리

듣지 못하는 운전자를 위해 차가 대신 듣고, 빛과 글자로 보여 주고, 필요하면 대신 말해 주는 Pleos Connect 앱입니다.

## 할 수 있는 일

| ID | 기능 | 동작 |
| --- | --- | --- |
| F1 | 바깥 소리 알림 | 사이렌과 경적을 감지해 큰 문구, 행동 안내, 실내 조명 리듬으로 알립니다 |
| F2 | 대화 자막 | 동승자의 말을 실시간 자막으로 보여 줍니다 |
| F3 | 대신 말하기 | 답장 버튼을 누르면 차 스피커로 말합니다 |
| F4 | 창문 너머 대화 | 정차 중 운전석 창문을 열면 바깥 사람과 대화하는 화면으로 바뀝니다 |
| F5 | 차량 소리 시각화 | 깜빡이 방치, 문 열림 주행, 경고를 글자와 조명으로 알립니다 |
| F6 | 놓친 소리 다시보기 | 정차 중에 이번 운행의 소리 기록을 시간순으로 봅니다 |

## APK

- 위치: `apk/` 폴더 또는 저장소 Releases
- Pleos Connect 전용입니다. 자동차용(Automotive) 기기와 에뮬레이터에서만 설치됩니다.

## 실행 방법

### 준비

1. Android Studio의 SDK Manager > SDK Update Sites에 Pleos Connect 시스템 이미지를 추가합니다.
   - Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
   - Windows, Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
2. Device Manager에서 Pleos Connect v2.1.0 AVD를 만듭니다. Default boot는 Cold, RAM은 4GB로 설정합니다.

### 설치

```bash
adb install -r -g apk/*.apk
```

앱은 사용자 10으로 실행됩니다. 명령줄에서 실행할 때는 `adb shell am start --user 10 -n app.visiblesound/.MainActivity`를 씁니다.

### 데모 보기 (가장 빠른 방법)

1. 기어가 P인 상태에서 앱을 열고 **설정**을 누릅니다.
2. **데모 모드**를 켜고 **대본 재생**을 누릅니다.
3. 약 70초 동안 사이렌, 동승자 자막, 대신 말하기, 경적, 깜빡이 경고, 창문 너머 대화가 순서대로 진행됩니다.
4. 끝나면 **기록** 탭에서 놓친 소리를 확인합니다.

### 실제 입력으로 시험하기

데모 모드를 끄면 차량 입력과 마이크로 동작합니다. 차량 상태는 Virtual Console로 바꿉니다.

```bash
adb forward tcp:8088 tcp:8088
# 브라우저에서 http://127.0.0.1:8088/ 을 엽니다
```

| 조작 | 결과 |
| --- | --- |
| Gear P, Window Position 앞 왼쪽 60% | 창문 너머 대화 화면으로 전환 |
| Gear D, Door Position 앞 왼쪽 열림 | "문이 열린 채 주행 중이에요" |
| Gear D, Turn Signal 좌 Blink, Steering 0, 20초 대기 | "방향지시등이 켜져 있어요" |
| 위 알림이 뜰 때 Ambient Brightness·Color | 리듬대로 바뀌었다가 원래 값으로 복구 |
| Seat Occupancy 설정 후 에뮬레이터 마이크로 말하기 | 해당 좌석이 강조된 자막 |
| Extended Controls > Microphone을 켜고 사이렌 음원 재생 | 사이렌 알림 |

## 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator 2.1.0 (API 34), 2560×1440 |
| 빌드 | JDK 17, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0 |
| SDK 레벨 | minSdk 28, compileSdk·targetSdk 35 |

```bash
./gradlew testDebugUnitTest     # 단위 테스트
./gradlew assembleRelease       # 서명 키가 없으면 디버그 키로 서명합니다
```

## 사용한 Pleos SDK와 API

| 기능 | SDK | 도메인·API |
| --- | --- | --- |
| F1, F5 조명 리듬 | Vehicle 2.3.2 | `Light`: `setAmbientLightBrightness`, `setAmbientLightBrightnessColor` |
| F2 대화 자막 | SpeechToText 3.3.1 | `SpeechToText` ON_DEVICE: `sendAudio`, `ResultListener.onUpdated` |
| F2 말한 좌석 | Vehicle 2.3.2 | `Seat`: `registerSeatOccupancy` |
| F3, F4 대신 말하기 | TextToSpeech 3.3.1 | `TextToSpeech`: `speak`, `EventListener` |
| F4 창문 | Vehicle 2.3.2 | `Window`: `registerWindowPosition` |
| F4, F5, F6 기어 | Vehicle 2.3.2 | `Gear`: `registerGearSelection` |
| F5 깜빡이 | Vehicle 2.3.2 | `TurnSignal`: `registerTurnSignalState`, `SteeringWheel`: `registerSteeringWheelAngle` |
| F5 문 열림 | Vehicle 2.3.2 | `Door`: `registerDoorPosition` |
| F5 경고 | Vehicle 2.3.2 | `Safety`: 안전벨트, 워셔액, 엔진오일, 브레이크액 |
| F6 위치·거리 | FusedLocation 1.1.3 | `FusedLocation`: `registerFusedLocationCallback` |

Pleos SDK에 없는 기능만 다른 기술로 보완합니다.

| 기능 | 사용 기술 | 이유 |
| --- | --- | --- |
| 소리 분류 | YAMNet, TensorFlow Lite | 사이렌·경적을 차 안에서 바로 판별합니다 |
| 마이크 입력 공유 | Android `AudioRecord` | 하나의 마이크를 소리 분류와 음성 인식이 함께 씁니다 |
| 주행 중 화면 제한 | Android `CarUxRestrictionsManager` | 시스템의 주행 제한 상태를 따릅니다 |

## 현재 한계

- **말한 좌석:** 공개 SDK는 누가 말했는지 알려 주지 않습니다. 탑승 좌석이 하나면 그 좌석을, 둘 이상이면 좌석 없이 "승객"으로 표시합니다.
- **소리 방향:** 마이크 하나로는 소리의 방향을 알 수 없어 실제 입력에서는 표시하지 않습니다. 데모 대본에서만 방향을 보여 줍니다.
- **차종 구분:** 모델의 세부 분류를 믿을 수 없어 실제 입력에서는 "긴급차량"으로만 알립니다.
- **F5 경고:** 공개 Safety API는 에뮬레이터 2.1.0에서 지원되지 않습니다(`ERROR_NOT_SUPPORTED_PROPERTY`). 에뮬레이터에서는 데모 대본으로 확인합니다.
- **백그라운드:** 앱이 화면에 보일 때 동작합니다. 에뮬레이터에서 후진 시 시스템 화면이 앱을 완전히 덮으면 마이크가 무음 처리되고, 약 12초 뒤에는 앱이 멈춥니다. 후진 중에는 소리를 알릴 수 없습니다.
- **시스템 창:** 에뮬레이터는 방향지시등을 켜면 앱 창 왼쪽 위에 시스템 창을 띄웁니다. 경고 글자가 가려지지 않게 그동안 카드를 그 아래로 옮깁니다. 창의 크기는 기본 디스플레이에서만 측정했고, 실차에서 같은지는 확인하지 못했습니다.

## 라이선스

사용한 글꼴과 모델의 라이선스는 [licenses/THIRD_PARTY_NOTICES.md](licenses/THIRD_PARTY_NOTICES.md)에 있습니다.
