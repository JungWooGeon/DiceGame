package com.selvashc.entertainment.dicegame;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;

import com.jawon.han.HanActivity;
import com.jawon.han.key.HanBrailleKey;
import com.jawon.han.key.keyboard.usb.USB2Braille;
import com.jawon.han.output.HanDevice;
import com.jawon.han.util.HimsCommonFunc;
import com.jawon.han.widget.HanApplication;
import com.jawon.han.widget.adapter.HanStringArrayAdapter;
import com.selvashc.entertainment.dicegame.databinding.ActivityMainBinding;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 *   메인화면
 *      - 주사위 개수를 선택하고, 주사위를 굴려 숫자 결과를 TTS 로 출력함
 **/
public class MainActivity extends HanActivity implements SensorEventListener {

    private static final float SHAKE_THRESHOLD_GRAVITY = 1.5F;
    private static final int VIBRATE_TIME = 4000;
    private static final int MAX_NUMBER_OF_DICE = 3;
    private static final int MAX_DICE_NUMBER = 6;

    private HanDevice hanDevice;
    // 주사위의 개수를 선택한 상태
    private boolean isReady = false;
    // 주사위를 굴린 후 다시 굴릴 준비가 되어 있는 상태
    private boolean isFinish = false;
    // 주사위의 숫자
    private int selectNum;
    // 주사위 개수만큼 효과음을 주기 위한 count
    private int setCount;

    // opening 음악, 주사위 효과음, 주사위 굴리기 음악
    private MediaPlayer openingPlayer;
    private MediaPlayer effectPlayer;
    private MediaPlayer diceRolling2;

    // 진동을 주기 위한 vibrator 와 진동 감지를 위한 sensor
    private Vibrator vibrator;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ActivityMainBinding binding;

    private SecureRandom random = new SecureRandom();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        binding.setActivity(this);

        hanDevice = HanApplication.getInstance(this).getHanDevice();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        initViews();
        initMediaPlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openingPlayer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        exitActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exitActivity();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int scanCode = USB2Braille.getInstance().convertUSBtoBraille(this, event);

        if (event.getAction() != KeyEvent.ACTION_UP)
            return super.dispatchKeyEvent(event);

        // 주사위 개수를 선택한 상태에서 엔터키를 누를 경우 주사위 굴리기 실행
        if (isReady && scanCode == HanBrailleKey.HK_ENTER) {
            isReady = false;
            diceRolling2.start();
            sensorManager.unregisterListener(this);
            vibrator.vibrate(VIBRATE_TIME);
            return true;
        }

        // 주사위를 굴린 후에 스페이스키나 엔터키를 누를 경우 주사위를 선택한 상태로 다시 셋팅 (음악 재생)
        if (isFinish && (scanCode == HanBrailleKey.HK_SPACE || scanCode == HanBrailleKey.HK_ENTER)) {
            setCount = selectNum;
            effectPlayer.start();
            return true;
        }

        // 종료키를 눌렀을 때, 주사위를 굴린 후라면 주사위 개수를 선택하는 목록으로 이동하고, 아니라면 activity 종료
        if (HimsCommonFunc.isExitKey(event.getScanCode(), event.getKeyCode())) {
            if (isReady || isFinish) {
                isReady = false;
                isFinish = false;
                hanDevice.displayAndPlayTTS(getString(R.string.plz_select_number_of_dice), true);
                binding.selectNumberOfDiceLayout.setVisibility(View.VISIBLE);
            } else
                finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void initViews() {
        List<String> itemList = new ArrayList<>();
        itemList.add(getString(R.string.select_number_of_dice) + ": " + getString(R.string.dice_one));
        itemList.add(getString(R.string.select_number_of_dice) + ": " + getString(R.string.dice_two));
        itemList.add(getString(R.string.select_number_of_dice) + ": " + getString(R.string.dice_three));
        itemList.add(getString(R.string.COMMON_MSG_EXIT));

        binding.selectNumberOfDiceListview.setAdapter(new HanStringArrayAdapter(this, android.R.layout.simple_list_item_1, itemList));
        binding.selectNumberOfDiceListview.setOnItemClickListener((parent, view, position, id) -> {
            // 끝내기 선택 시 종료
            if (position == MAX_NUMBER_OF_DICE) {
                finish();
                return;
            }

            // 선택한 주사위 개수를 저장하고, 다음 효과음 재생
            selectNum = position + 1;
            hanDevice.displayAndPlayTTS(String.format(getString(R.string.complete_select), selectNum), true);
            binding.selectNumberOfDiceLayout.setVisibility(View.INVISIBLE);
            setCount = selectNum;
            effectPlayer.start();
        });
    }

    private void initMediaPlayer() {
        openingPlayer = MediaPlayer.create(this, R.raw.dice_opening);
        openingPlayer.setOnCompletionListener(mp -> {
            mp.release();
            hanDevice.displayAndPlayTTS(getString(R.string.plz_select_number_of_dice), true);
            binding.selectNumberOfDiceLayout.setVisibility(View.VISIBLE);
            binding.selectNumberOfDiceListview.requestFocus();
        });

        effectPlayer = MediaPlayer.create(this, R.raw.dice_effect);
        effectPlayer.setOnCompletionListener(mp -> {
            if (--setCount != 0)
                effectPlayer.start();
            else {
                hanDevice.displayAndPlayTTS(getString(R.string.ready_enter), true);
                isReady = true;
                isFinish = false;
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });

        diceRolling2 = MediaPlayer.create(this, R.raw.dice_rolling2);
        diceRolling2.setOnCompletionListener(mp -> {
            // 1 ~ 6 사이의 난수 생성 후 playTTS
            StringBuilder playDice = new StringBuilder();
            for (int i = 0; i < selectNum; i++)
                playDice.append((random.nextInt(MAX_DICE_NUMBER)) + 1).append(" ");
            playDice.append(getString(R.string.plz_enter_space));
            hanDevice.displayAndPlayTTS(playDice.toString(), true);
            isFinish = true;
        });
    }

    private void exitActivity() {
        // mediaPlayer 들을 release 하고, vibrator 를 취소 한 후에 종료
        openingPlayer.release();
        effectPlayer.release();
        diceRolling2.release();
        vibrator.cancel();
        sensorManager.unregisterListener(this);
        moveTaskToBack(true);
        finishAndRemoveTask();
        System.exit(0);
    }

    // 기기 흔들림을 감지 (registerListener : 감지 실행 역할, unregisterListener : 감지 취소 역할 로 사용)
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float gravityX = event.values[0] / SensorManager.GRAVITY_EARTH;
            float gravityY = event.values[1] / SensorManager.GRAVITY_EARTH;
            float gravityZ = event.values[2] / SensorManager.GRAVITY_EARTH;

            Float f = gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ;
            double squaredD = Math.sqrt(f.doubleValue());
            float gForce = (float) squaredD;
            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                isReady = false;
                diceRolling2.start();
                sensorManager.unregisterListener(this);
                vibrator.vibrate(VIBRATE_TIME);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // no use
    }
}
