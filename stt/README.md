# AIQ.TALK STT Android Example

This repository contains simple Android demo program to call a streaming speech-to-text request using AIQ.TALK STT service. For now, we only provide streaming example.

## Note

To use AIQ.TALK STT request, you should enter API key in [MainActivity.java](app/src/main/java/aiq/speech/example/android/MainActivity.java). If you don't have API key, get one from [AIQ Console](https://aiq.skelterlabs.com/console).

```java
    /**
     * STT 사용을 위한 API Key. STT 봇 프로젝트 설정에 사용한 값을 사용해야 한다.
     */
    private static final String API_KEY = "<<Your API Key>>";
```

## Build

To build this app, enter the following command in the `{root of repository}/stt`

```bash
./gradlew clean build
```
