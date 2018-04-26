package com.baidu.android.voicedemo.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.baidu.android.voicedemo.control.MyRecognizer;
import com.baidu.android.voicedemo.recognization.ChainRecogListener;
import com.baidu.android.voicedemo.recognization.MessageStatusRecogListener;
import com.baidu.android.voicedemo.recognization.offline.OfflineRecogParams;
import com.baidu.android.voicedemo.util.Logger;
import com.baidu.voicerecognition.android.ui.BaiduASRDigitalDialog;
import com.baidu.voicerecognition.android.ui.DigitalDialogInput;
import com.baidu.voicerecognition.android.ui.SimpleTransApplication;

import java.util.ArrayList;
import java.util.Map;

/**
 * UI 界面调用
 * <p>
 * 本类仅仅初始化及释放MyRecognizer，具体识别逻辑在BaiduASRDialog。对话框UI在BaiduASRDigitalDialog
 * 依赖SimpleTransApplication 在两个activity中传递输入参数
 * <p>
 * <p>
 * <p>
 * Created by fujiayi on 2017/10/17.
 */

public class ActivityUiDialog extends ActivityOnline {

    {
        DESC_TEXT = "多了UI 对话框。使用在线普通识别功能(含长语音)\n" +
                "请先测试“在线识别”界面\n" +
                "识别逻辑在BaiduASRDialog\n" +
                "\n" +
                "相关资源文件名以bdsppech_开头";
    }

    /**
     * 对话框界面的输入参数
     */
    private DigitalDialogInput input;
    /**
     * 有2个listner，一个是用户自己的业务逻辑，如MessageStatusRecogListener。另一个是UI对话框的。
     * 使用这个ChainRecogListener把两个listener和并在一起
     */
    private ChainRecogListener listener;

    /**
     * 在onCreate中调用。初始化识别控制类MyRecognizer
     */
    protected void initRecog() {
        listener = new ChainRecogListener();
        // DigitalDialogInput 输入 ，MessageStatusRecogListener可替换为用户自己业务逻辑的listener
        listener.addListener(new MessageStatusRecogListener(handler));
        myRecognizer = new MyRecognizer(this, listener); // DigitalDialogInput 输入
        apiParams = getApiParams();
        status = STATUS_NONE;
        if (enableOffline) {
            myRecognizer.loadOfflineEngine(OfflineRecogParams.fetchOfflineParams());
        }
    }

    /**
     * 开始录音，点击“开始”按钮后调用。
     */
    @Override
    protected void start() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        Map<String, Object> params = apiParams.fetch(sp);  // params可以手动填入

        // BaiduASRDigitalDialog的输入参数
        input = new DigitalDialogInput(myRecognizer, listener, params);

        Intent intent = new Intent(this, BaiduASRDigitalDialog.class);
        // 在BaiduASRDialog中读取
        ((SimpleTransApplication) getApplicationContext()).setDigitalDialogInput(input);
        // intent.putExtra(BaiduASRDigitalDialog.PARAM_DIALOG_THEME, BaiduASRDigitalDialog.THEME_ORANGE_DEEPBG); //修改对话框样式

        startActivityForResult(intent, 2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String message = "对话框的识别结果：";
        if (resultCode == RESULT_OK) {
            ArrayList results = data.getStringArrayListExtra("results");
            if (results != null && results.size() > 0) {
                message += results.get(0);
            }
        } else {
            message += "出现错误";
        }
        Logger.info(message);
    }
}