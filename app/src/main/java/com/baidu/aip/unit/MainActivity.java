/*
 * Copyright (C) 2017 Baidu, Inc. All Rights Reserved.
 */
package com.baidu.aip.unit;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.baidu.aip.chatkit.ImageLoader;
import com.baidu.aip.chatkit.message.MessageInput;
import com.baidu.aip.chatkit.message.MessagesList;
import com.baidu.aip.chatkit.message.MessagesListAdapter;

import com.baidu.aip.chatkit.model.Hint;
import com.baidu.aip.chatkit.model.Message;
import com.baidu.aip.chatkit.model.User;
import com.baidu.aip.unit.exception.UnitError;
import com.baidu.aip.unit.listener.OnResultListener;
import com.baidu.aip.unit.model.AccessToken;
import com.baidu.aip.unit.model.CommunicateResponse;
import com.baidu.aip.unit.model.Scene;

import com.baidu.aip.unit.widget.BasePopupWindow;
import com.baidu.android.voicedemo.util.Logger;
import com.baidu.voicerecognition.android.ui.BaiduASRDigitalDialog;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity
        implements MessagesListAdapter.OnLoadMoreListener, MessageInput.InputListener,
        MessageInput.VoiceInputListener {

    private static final int SCENE_SWEEPER_ROBOT = 20664;
    private static final int SCENE_NAVIGATE = 3109;
    private static final int SCENE_CONSTELLATION = 10538;

    protected ImageLoader imageLoader;
    protected MessagesListAdapter<Message> messagesAdapter;
    protected HintAdapter hintAdapter;

    private TextView titleTv;
    private RelativeLayout titleRl;
    private RelativeLayout rootRl;
    private BasePopupWindow popupWindow;
    private MessagesList messagesList;
    private MessageInput messageInput;

    private User sender;
    private User cs;
    private String sessionId = "";
    private Scene curScene;
    private int id = 0;
    private String accessToken;
    private List<Message> waitList = new ArrayList<>();
    private Map<Integer, String> sceneMap = new HashMap<Integer, String>();

    private ListView dataListview;
    private List<Scene> dataList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initData();

        findView();
        initAdapter();

        messageInput.getInputEditText().setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND){
                    onSubmit(v.getEditableText());
                    v.setText("");
                }
                return true;
            }
        });


        hintAdapter = new HintAdapter(this);

        changeScene(dataList.get(1));
        initPopupWindow();

        initAccessToken();
    }

    // 演示数据，开发者可到ai.baidu.com 官网理解与交互技术(UNIT)板块申请并训练自己的场景机器人
    private void initData() {
        sender = new User("0", "kf", "", true);
        cs = new User("1", "客服", "", true);

        sceneMap.put(SCENE_SWEEPER_ROBOT, "气象台");
        sceneMap.put(SCENE_NAVIGATE, "车载导航");
        sceneMap.put(SCENE_CONSTELLATION, "星座运势查询");

        dataList = new ArrayList<Scene>();
        dataList.add(new Scene(SCENE_SWEEPER_ROBOT , "气象台"));
        dataList.add(new Scene(SCENE_NAVIGATE , "车载导航"));
        dataList.add(new Scene(SCENE_CONSTELLATION , "星座运势查询"));

    }

    private void findView() {
        this.rootRl = (RelativeLayout) findViewById(R.id.root);
        this.titleRl = (RelativeLayout) findViewById(R.id.title_rl);
        this.titleTv = (TextView) findViewById(R.id.title_tv);
        this.messagesList = (MessagesList) findViewById(R.id.messagesList);
        messageInput = (MessageInput) findViewById(R.id.input);

        messageInput.setInputListener(this);
        messageInput.setAudioInputListener(this);
//        hintRV = (RecyclerView) findViewById(R.id.hint_rv);
        titleRl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupWindow.getPopupWindow().setAnimationStyle(R.style.animTranslate);
                popupWindow.showAtLocation(rootRl, Gravity.BOTTOM, 0, 0);
                WindowManager.LayoutParams lp=getWindow().getAttributes();
                lp.alpha = 0.3f;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                getWindow().setAttributes(lp);
            }
        });

    }

    /**
     * 为了防止破解app获取ak，sk，建议您把ak，sk放在服务器端。
     */
    private void initAccessToken() {
        APIService.getInstance().init(getApplicationContext());
        APIService.getInstance().initAccessToken(new OnResultListener<AccessToken>() {
            @Override
            public void onResult(AccessToken result) {
                accessToken = result.getAccessToken();
                Log.i("MainActivity", "AccessToken->" + result.getAccessToken());
                if (!TextUtils.isEmpty(accessToken)) {
                    resendWaitList();
                }

            }

            @Override
            public void onError(UnitError error) {
                Log.i("wtf", "AccessToken->" + error.getErrorMessage());
            }
        }, "cOMXwAsnrVSrPWXtnqOhMssE", "p9nmLUp9z9uQwjumGDiY0QMctbiRRIdz");
//        }, "ruKQt3mup9uvn0NACO1Qm3V7", "ZzOlRNdKYBE7hBwaVO7qRd3euqoAveN5");
    }

    /**
     * 重发未发送成功的消息
     */
    private void resendWaitList() {
        for (Message message : waitList) {
            sendMessage(message);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Message message = new Message(String.valueOf(id++), cs, "主人，很高兴为你服务！", new Date());
        // messagesAdapter.addToStart(message, true);
    }


    @Override
    public boolean onSubmit(CharSequence input) {

        Message message = new Message(String.valueOf(id++), sender, input.toString(), new Date());
        messagesAdapter.addToStart(message, true);

        sendMessage(message);
        return true;
    }

    @Override
    public void onVoiceInputClick() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager
                .PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }

        start();
    }

    private void initAdapter() {
        messagesAdapter = new MessagesListAdapter<>(sender.getId(), imageLoader);
        // messagesAdapter.enableSelectionMode(this);
        messagesAdapter.setLoadMoreListener(this);
        messagesAdapter.setOnHintClickListener(new MessagesListAdapter.OnHintClickListener<Message>() {
            @Override
            public void onHintClick(String hint) {
                Message message = new Message(String.valueOf(id++), sender, hint, new Date());
                sendMessage(message);
                messagesAdapter.addToStart(message, true);
            }
        });
        messagesAdapter.registerViewClickListener(R.id.messageUserAvatar,
                new MessagesListAdapter.OnMessageViewClickListener<Message>() {
                    @Override
                    public void onMessageViewClick(View view, Message message) {
                        //                        AppUtils.showToast(MainActivity.this,
                        //                                message.getUser().getName() + " avatar click",
                        //                                false);
                    }
                });
        this.messagesList.setAdapter(messagesAdapter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onLoadMore(int page, int totalItemsCount) {

    }

    /**
     * 切换场景
     *
     * @param scene
     */
    private void changeScene(Scene scene) {

        curScene = scene;
        titleTv.setText(scene.getName());
        System.out.println("切换场景名称: "+scene.getName());
        messagesAdapter.clear();

        sessionId = "";
        Message message = new Message(String.valueOf(id++), sender, "你好", new Date());
        sendMessage(message);
    }

    private MessagesListAdapter.Formatter<Message> getMessageStringFormatter() {
        return new MessagesListAdapter.Formatter<Message>() {
            @Override
            public String format(Message message) {
                String createdAt = new SimpleDateFormat("MMM d, EEE 'at' h:mm a", Locale.getDefault())
                        .format(message.getCreatedAt());

                String text = message.getText();
                if (text == null) {
                    text = "[attachment]";
                }

                return String.format(Locale.getDefault(), "%s: %s (%s)",
                        message.getUser().getName(), text, createdAt);
            }
        };
    }

    private void sendMessage(Message message) {

        if (TextUtils.isEmpty(accessToken)) {
            waitList.add(message);
            return;
        }

        APIService.getInstance().communicate(new OnResultListener<CommunicateResponse>() {
            @Override
            public void onResult(CommunicateResponse result) {

                handleResponse(result);
            }

            @Override
            public void onError(UnitError error) {

            }
        }, curScene.getId(), message.getText(), sessionId);

    }

    private void handleResponse(CommunicateResponse result) {
        if (result != null) {
            sessionId = result.sessionId;

            //  如果有对于的动作action，请执行相应的逻辑
            List<CommunicateResponse.Action> actionList = result.actionList;
            if (actionList.size() > 1) {
                Message message = new Message(String.valueOf(id++), cs, "", new Date());
                for (CommunicateResponse.Action action : actionList) {

                    if (!TextUtils.isEmpty(action.say)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(action.say);

                        Message actionMessage = new Message("", cs, sb.toString(), new Date());

                        for (String hintText : action.hintList) {

                            actionMessage.getHintList().add(new Hint(hintText));
                        }
                        message.getComplexMessage().add(actionMessage);
                    }
                }
                messagesAdapter.addToStart(message, true);
            } else if (actionList.size() == 1){
                CommunicateResponse.Action action = actionList.get(0);
                if (!TextUtils.isEmpty(action.say)) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(action.say);

                    Message message = new Message(String.valueOf(id++), cs, sb.toString(), new Date());
                    messagesAdapter.addToStart(message, true);
                    for (String hintText : action.hintList) {

                        message.getHintList().add(new Hint(hintText));
                    }

                }

                // 执行自己的业务逻辑
                if ("start_work_satisfy".equals(action.actionId)) {
                    Log.i("wtf", "开始扫地");
                } else if ("stop_work_satisfy".equals(action.actionId)) {
                    Log.i("wtf", "停止工作");
                } else if ("move_action_satisfy".equals(action.actionId)) {
                    Log.i("wtf", "移动");
                } else if ("timed_charge_satisfy".equals(action.actionId)) {
                    Log.i("wtf", "定时充电");
                } else if ("timed_task_satisfy".equals(action.actionId)) {
                    Log.i("wtf", "定时扫地");
                } else if ("sing_song_satisfy".equals(action.actionId)) {
                    Log.i("wtf", "唱歌");
                }

                if (!TextUtils.isEmpty(action.mainExe)) {
                    Toast.makeText(MainActivity.this, "请执行函数：" + action.mainExe, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void initPopupWindow() {
        // get the height of screen
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48 * dataList.size() + 3, metrics);
        // create popup window
        popupWindow = new BasePopupWindow(this, R.layout.scene_list_view, ViewGroup.LayoutParams.MATCH_PARENT,
                height) {
            @Override
            protected void initView() {
                View view=getContentView();
                dataListview = (ListView) view.findViewById(R.id.data_list);
                dataListview.setAdapter(new ArrayAdapter<Scene>(MainActivity.this, R.layout.item_popup_view, dataList));
            }

            @Override
            protected void initEvent() {
                dataListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        popupWindow.getPopupWindow().dismiss();
                        Scene scene = dataList.get(position);
                        changeScene(scene);
                    }
                });
            }

            @Override
            protected void initWindow() {
                super.initWindow();
                PopupWindow instance = getPopupWindow();
                instance.setOnDismissListener(new PopupWindow.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        lp.alpha = 1.0f;
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                        getWindow().setAttributes(lp);
                    }
                });
            }
        };
    }



    /**
     * 开始录音，点击“开始”按钮后调用。
     */
    protected void start() {
        Intent intent = new Intent(this, BaiduASRDigitalDialog.class);
        // intent.putExtra(BaiduASRDigitalDialog.PARAM_DIALOG_THEME, BaiduASRDigitalDialog.THEME_ORANGE_DEEPBG); //修改对话框样式

        startActivityForResult(intent, 2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String text = "";
        if (resultCode == RESULT_OK) {
            ArrayList results = data.getStringArrayListExtra("results");
            if (results != null && results.size() > 0) {
                text += results.get(0);
            }
            Message message = new Message(String.valueOf(id++), sender, text, new Date());
            sendMessage(message);
            messagesAdapter.addToStart(message, true);
        } else {
            Logger.info("出现错误");
        }
    }

}
