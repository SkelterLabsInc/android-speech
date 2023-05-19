package aiq.speech.example.android;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.media.AudioRecord.ERROR;
import static android.media.AudioRecord.ERROR_BAD_VALUE;
import static android.media.AudioRecord.STATE_INITIALIZED;
import static android.media.MediaRecorder.AudioSource.MIC;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.makeText;
import static com.google.cloud.speech.v1.RecognitionConfig.AudioEncoding.LINEAR16;

import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.SpeechGrpc;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;

public class MainActivity extends AppCompatActivity
    implements StreamObserver<StreamingRecognizeResponse> {
  private final int REQUEST_RECORD_AUDIO = 300;
  /** STT 서버의 호스트명. SaaS의 경우 aiq.skelterlabs.com을 사용하지만, 자체 구축(on-premise)의 경우 해당 호스트명을 사용한다. */
  private static final String HOSTNAME = "aiq.skelterlabs.com";
  /** STT 서버 접속을 위한 포트 번호 */
  private static final int PORT = 443;
  /** STT 사용을 위한 API Key. STT 봇 프로젝트 설정에 사용한 값을 사용해야 한다. */
  private static final String API_KEY = "<<Your API Key>>";
  /** 현재 인식 중인지 여부 */
  private boolean isRunning = false;
  /** gRpc 통신 채널 */
  private ManagedChannel channel = null;
  /** gRpc 비동기 통신 방식에서 수신을 담당하는 객체 */
  private StreamObserver<StreamingRecognizeRequest> sttStream = null;
  /** 안드로이드에서 로그 출력을 위한 태그 */
  private static final String TAG = "STT-client";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // Add a click handler to start button
    findViewById(R.id.start_button).setOnClickListener(view -> tryStart());

    // Add a click handler to stop button
    findViewById(R.id.stop_button).setOnClickListener(view -> stop());
  }

  /** 마이크 장치에 대한 권한을 확인후 - 권한이 없는 경우 사용자 승인을 요청한다. - 권한이 있는 경우 인식을 시작한다. */
  private void tryStart() {
    // 마이크 권한을 확인한다.
    if (checkSelfPermission(RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      // 사용자 승인을 요청한다.
      requestPermissions(new String[] {RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
    } else {
      start();
    }
  }

  /** 인식을 시작한다. */
  private void start() {
    // 현재 이미 대화 중인지 확인한다.
    if (isRunning) {
      // 현재 대화 중임을 사용자에게 알린다.
      makeText(this, "Already running", LENGTH_LONG).show();
      return;
    }
    // 대화를 시작한다.
    try {
      startGrpc();
      startAudioIn();
    } catch (final Throwable ex) {
      Log.e(TAG, "Unexpected exception:", ex);
      makeText(this, "Fail to start!! Check log", LENGTH_LONG).show();
    }
  }

  /**
   * STT 서버와 gRpc 통식을 시작한다.
   *
   * @throws Exception 통신 과정에서 발생하는 예외들. 예외에 따른 처리를 하지 않는다.
   */
  private void startGrpc() throws Exception {
    // 통신을 위한 보안을 위한 TLS를 사용하는 경우, SSLContext 객체를 사용해야 한다.
    final SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    // 보안 객체 초기화
    sslContext.init(null, null, null);

    // 통신 채널을 연결한다.
    channel =
        OkHttpChannelBuilder.forAddress(HOSTNAME, PORT)
            .maxInboundMessageSize(16 * 1024 * 1024)
            .overrideAuthority(HOSTNAME)
            .useTransportSecurity()
            .sslSocketFactory(sslContext.getSocketFactory())
            .build();

    // gRpc API 객체를 생성하고, API Key로 인증한다.
    final SpeechGrpc.SpeechStub stub =
        SpeechGrpc.newStub(channel)
            .withCallCredentials(
                new CallCredentials() {
                  @Override
                  public void applyRequestMetadata(
                      RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
                    try {
                      final Metadata metadata = new Metadata();
                      metadata.put(
                          Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER), API_KEY);
                      applier.apply(metadata);
                    } catch (final Throwable ex) {
                      applier.fail(Status.UNAUTHENTICATED.withCause(ex));
                    }
                  }

                  @Override
                  public void thisUsesUnstableApi() {}
                });

    // 수신 객체
    final StreamObserver<StreamingRecognizeRequest> stream = stub.streamingRecognize(this);

    // 데이터 교환을 위한 설정들 전달한다. mono 16KHz 16bit 데이타로 전달할 것임을 알린다.
    final StreamingRecognitionConfig streamingRecognitionConfig =
        StreamingRecognitionConfig.newBuilder()
            .setConfig(
                RecognitionConfig.newBuilder()
                    .setEncoding(LINEAR16)
                    .setSampleRateHertz(16_000)
                    .setLanguageCode("ko-KR")
                    .build())
            .build();
    // 데이타를 전송한다.
    stream.onNext(
        StreamingRecognizeRequest.newBuilder()
            .setStreamingConfig(streamingRecognitionConfig)
            .build());
    sttStream = stream;
  }
  /** 안드로이드의 마이크로부터 mono 16KHz 16bit 샘플링 음원을 읽을 수 있도록 한다. */
  private void startAudioIn() {
    isRunning = false;
    /** 오디오 데이타를 처리하기 위한 객체. */
    final Runnable recorder =
        () -> {
          // 샘플링 비율은 16KHz
          final int sampingRate = 16_000;

          // 오디오 음원을 위한 버퍼 크기를 계산한다.
          int bufferSize =
              AudioRecord.getMinBufferSize(sampingRate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT);
          if (bufferSize == ERROR || bufferSize == ERROR_BAD_VALUE) {
            bufferSize = 2 * sampingRate;
          }

          // 오디오 입력을 제어하는 객체를 생성한다.
          final AudioRecord audioRecord =
              new AudioRecord(MIC, sampingRate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, bufferSize);

          if (audioRecord.getState() == STATE_INITIALIZED) { // 정상적으로 생성되었다면
            audioRecord.startRecording();
            final short[] shortArray = new short[bufferSize / 2];
            isRunning = true;
            /*
             * 처리 중 일 때, 오디오 장치에서 데이타를 읽어서 STT 서버에게 전달한다.
             * 대화 상태 변수는 사용자가 종료 버튼을 누를 때 바뀐다.
             */
            while (isRunning) {
              // 오디오 장치에서 음원을 읽는다.
              final int nRead = audioRecord.read(shortArray, 0, shortArray.length);

              // 버퍼를 생성하고 데이타를 복사한다.
              final ByteBuffer buffer = ByteBuffer.allocate(2 * nRead);
              buffer.order(ByteOrder.LITTLE_ENDIAN);
              final ShortBuffer shortBuffer = buffer.asShortBuffer();
              shortBuffer.put(shortArray, 0, nRead);
              final byte[] bytes = buffer.array();
              // gRpc 데이타 객체
              final StreamingRecognizeRequest req =
                  StreamingRecognizeRequest.newBuilder()
                      .setAudioContent(ByteString.copyFrom(bytes))
                      .build();
              if (isRunning) {
                // STT 서버에 데이타를 전송한다.
                sttStream.onNext(req);
              }
            }

            // 마이크의 녹음을 중단한다.
            audioRecord.stop();
            // 장치를 해제한다.
            audioRecord.release();
          }
        };

    // 사용자 조작에 방해되지 않도록 백그라운드 쓰레드에서 처리한다.
    new Thread(recorder).start();
  }

  /** gRpc 통신을 종료하고, 마이크 입력을 중단한다. */
  public void stop() {
    stopAudioIn();
    stopGrpc();
  }

  /** gRpc 통신을 종료한다. */
  private void stopGrpc() {
    sttStream.onCompleted();
  }

  /** 마이크로부터 오디오 입력을 중단한다. */
  private void stopAudioIn() {
    isRunning = false;
  }

  /**
   * gRpc의 데이터 수신의 call back 함수
   *
   * @param res 전달받은 데이타
   */
  @Override
  public void onNext(StreamingRecognizeResponse res) {
    final TextView resultText = findViewById(R.id.result_text);
    res.getResultsList()
        .forEach(
            (StreamingRecognitionResult result) -> {
              resultText.append(result.getAlternatives(0).getTranscript() + "\n");
            });
  }

  /**
   * gRpc 통신 과정에 예외가 발생한 경우에 대한 call back 함수
   *
   * @param t 발생한 예외
   */
  @Override
  public void onError(Throwable t) {
    Log.e(TAG, "Unexpected exception", t);
    stop();
  }

  /** gRpc 통신이 종료된 경우의 call back 함수 */
  @Override
  public void onCompleted() {
    Log.d(TAG, this.toString() + " completed");
    // 이미 통신은 종료되었으므로 오디오만 종료함
    stopAudioIn();
  }
}
