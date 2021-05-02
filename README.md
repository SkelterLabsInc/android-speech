# AIQ.TALK STT Android Example

This repository contains simple Android demo program to call a streaming speech-to-text request using AIQ.TALK STT service. For now, we only provide streaming example.

## Note

To use AIQ.TALK STT request, you should enter API Key in [MainActivity.kt](app/src/main/java/aiq/speech/example/android/MainActivity.kt). If you don't have API key, get one from [AIQ Console](https://aiq.skelterlabs.com/console).

```kotlin
    companion object {
        const val REQUEST_RECORD_AUDIO = 300
        const val API_KEY = "<<Your API Key>>"  // Enter your API key
        const val HOSTNAME = "aiq.skelterlabs.com"
        const val PORT = 443
        const val TAG = "speech-android"

        init {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        }
    }
```

## Build

To build this app, enter the following command in the root of repository.

```bash
./gradlew clean build
```

## License

Apache Version 2.0

See [LICENSE](LICENSE)
