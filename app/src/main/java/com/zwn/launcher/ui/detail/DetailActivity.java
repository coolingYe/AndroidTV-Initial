package com.zwn.launcher.ui.detail;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.danikula.videocache.HttpProxyCacheServer;
import com.zee.manager.IZeeCallback;
import com.zee.manager.IZeeManager;
import com.zeewain.base.Views.CustomNormalDialog;
import com.zeewain.base.config.BaseConstants;
import com.zeewain.base.model.LoadState;
import com.zeewain.base.ui.BaseActivity;
import com.zeewain.base.utils.CommonUtils;
import com.zeewain.base.utils.DateTimeUtils;
import com.zeewain.base.utils.DensityUtils;
import com.zeewain.base.utils.FileUtils;
import com.zeewain.base.utils.GlideApp;
import com.zeewain.base.utils.NetworkUtil;
import com.zwn.launcher.R;
import com.zwn.launcher.ZeeApplication;
import com.zwn.launcher.data.DataRepository;
import com.zwn.launcher.data.protocol.request.ProDetailReq;
import com.zwn.launcher.data.protocol.request.PublishReq;
import com.zwn.launcher.data.protocol.request.UpgradeReq;
import com.zwn.launcher.data.protocol.response.AlgorithmInfoResp;
import com.zwn.launcher.data.protocol.response.ModelInfoResp;
import com.zwn.launcher.data.protocol.response.ProDetailResp;
import com.zwn.launcher.data.protocol.response.PublishResp;
import com.zwn.launcher.databinding.ActivityDetailBinding;
import com.zwn.launcher.ui.loading.LoadingPluginActivity;
import com.zwn.launcher.utils.DownloadHelper;
import com.zwn.lib_download.DownloadListener;
import com.zwn.lib_download.DownloadService;
import com.zwn.lib_download.db.CareController;
import com.zwn.lib_download.model.DownloadInfo;
import com.zwn.user.data.protocol.request.CollectReq;
import com.zwn.user.data.protocol.request.RemoveCollectReq;
import com.zwn.user.data.protocol.response.AkSkResp;
import com.zwn.user.data.protocol.response.FavoritesResp;
import com.zwn.user.ui.LoginCenterActivity;
import com.zwn.user.utils.UserCenterUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

public class DetailActivity extends BaseActivity {
    private static final String TAG = "DetailActivity";
    private final static int REQUEST_CODE_LOGIN = 1000;
    private ProDetailViewModel detailViewModel;
    private ActivityDetailBinding binding;
    private int key = 0;
    private final StringBuilder mFormatBuilder = new StringBuilder();   //????????????????????????
    private final Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    private String videoUrl;
    private String skuId;
    private DownloadService.DownloadBinder downloadBinder = null;
    private boolean isProductRelease = true;
    private String currentFileId;
    private String lastVersion;
    private boolean isClickEnable = false;
//    private String favoriteId;
    private boolean isAddCollected;
    private CustomNormalDialog upgradeDialog;
    private boolean isRequest=false;
    private final Handler handler = new Handler();
    private final Runnable runnable = new Runnable() {
        public void run() {
            if (binding.videoLayout.videoView.isPlaying()) {
                int current = binding.videoLayout.videoView.getCurrentPosition();
                binding.videoLayout.timeSeekBar.setProgress(current);
                binding.videoLayout.tvPlayTime.setText(DateTimeUtils.formatToTimeString(binding.videoLayout.videoView.getCurrentPosition()));
            }
            handler.postDelayed(runnable, 500);
        }
    };

    private final DownloadListener downloadListener = new DownloadListener() {
        @Override
        public void onProgress(String fileId, int progress, long loadedSize, long fileSize) {
            if (fileId.equals(currentFileId)) {
                runOnUiThread(() -> {
                    if (progress == 100) {
                        binding.downloadIcon.setImageResource(R.drawable.img_experience_select);
                        binding.downloadPro.setText("????????????");
                    } else {
                        binding.downloadIcon.setImageResource(R.drawable.img_downloading_select);
                        binding.downloadPro.setText(String.format("?????????(%s%%)", progress));
                    }
                });
            }
        }

        @Override
        public void onSuccess(String fileId, int type, File file) {}

        @Override
        public void onFailed(String fileId, int type, int code) {
            if (fileId.equals(currentFileId)) {
                runOnUiThread(() -> {
                    if(!NetworkUtil.isNetworkAvailable(DetailActivity.this)){
                        showToast("?????????????????????");
                    }
                    binding.downloadIcon.setImageResource(R.drawable.img_continue_select);
                    binding.downloadPro.setText("??????");
                });
            }
        }

        @Override
        public void onPaused(String fileId) {}

        @Override
        public void onCancelled(String fileId) {}

        @Override
        public void onUpdate(String fileId) {}
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            downloadBinder = (DownloadService.DownloadBinder) service;
            if (downloadBinder != null) {
                downloadBinder.registerDownloadListener(downloadListener);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) { }
    };


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DensityUtils.autoWidth(getApplication(), this);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_detail);

        skuId = getIntent().getStringExtra("skuId");
        if (TextUtils.isEmpty(skuId)){
            finish();
            return;
        }

        bindService();
        bindManagerService();

        ProDetailViewModelFactory factory = new ProDetailViewModelFactory(DataRepository.getInstance());
        detailViewModel = ViewModelProviders.of(this, factory).get(ProDetailViewModel.class);

//        AutoUtils.auto(binding.videoLayout.rlVideoRoot);

        binding.productImage.setVisibility(View.VISIBLE);
        binding.videoLayout.rlVideoRoot.setVisibility(View.GONE);

        initClickListener();
        initFocusListener();
        initViewObservable();

        detailViewModel.getProDetailInfo(new ProDetailReq(skuId));
    }

    private void initClickListener(){
        binding.rlDownload.setOnClickListener(v -> {
            if (!CommonUtils.isUserLogin()) {
                showToast("????????????????????????");
                Intent intent = new Intent(DetailActivity.this, LoginCenterActivity.class);
                startActivityForResult(intent, REQUEST_CODE_LOGIN);
                return;
            }

            if (!isProductRelease) {
                showToast("????????????????????????????????????");
                return;
            }

            if(!isClickEnable){
                return;
            }

            if(handleAlgorithmLib()) {
                if (detailViewModel.upgradeResp != null) {//????????????
                    handleUpgrade();
                } else {
                    handleDownload();
                }
            }
        });

        binding.rlCollect.setOnClickListener(v -> {
            if (!CommonUtils.isUserLogin()) {
                showToast("????????????????????????");
                Intent intent = new Intent(DetailActivity.this, LoginCenterActivity.class);
                startActivityForResult(intent, REQUEST_CODE_LOGIN);
                return;
            }
            //????????????????????????
            if (isRequest){
                return;
            }

            CollectReq collectReq = new CollectReq(skuId, detailViewModel.proDetailResp.getProductTitle(), detailViewModel.proDetailResp.getUseImgUrl());
            if (!isAddCollected) {
                isRequest = true;
                detailViewModel.addFavorites(collectReq);
            } else if (!TextUtils.isEmpty(skuId)) {
                isRequest = true;
                List<String> tidList = new ArrayList<>();
                tidList.add(skuId);
                RemoveCollectReq removeCollectReq = new RemoveCollectReq(tidList);
                detailViewModel.removeFavorites(removeCollectReq);
            }
        });

        binding.rlShare.setOnClickListener(v -> showToast(R.string.not_support_now));

        binding.btnRefresh.setOnClickListener(v -> {
                detailViewModel.getProDetailInfo(new ProDetailReq(skuId));
        });
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void initFocusListener(){
        binding.rlDownload.setOnFocusChangeListener(this::controlFocusAnimation);
        binding.rlShare.setOnFocusChangeListener(this::controlFocusAnimation);
        binding.rlCollect.setOnFocusChangeListener(this::controlFocusAnimation);
        binding.productImage.setOnFocusChangeListener(this::controlFocusAnimation);
        binding.description.setMovementMethod(ScrollingMovementMethod.getInstance());
        binding.description.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                binding.layoutWeb.setBackground(getDrawable(R.drawable.selected_bg_web));
            } else {
                binding.layoutWeb.setBackground(getDrawable(R.drawable.un_selected_bg_web));
            }
        });

        binding.videoLayout.rlVideoRoot.setOnFocusChangeListener((v, hasFocus) -> {
            if (binding.videoLayout.videoView.isPlaying()) {
                controlFocusAnimation(v, hasFocus);
            }
        });
    }

    private void initViewObservable() {
        detailViewModel.mDetailLoadState.observe(this, loadState -> {
            if(LoadState.Loading == loadState) {
                binding.rlRootLoading.setVisibility(View.VISIBLE);
                binding.loadingViewDetail.startAnim();
            } else if (LoadState.Success == loadState) {
                if (detailViewModel.proDetailResp.getPutawayStatus() == 1 && CommonUtils.isUserLogin()) {
                    detailViewModel.getPublishVersionInfo(new PublishReq(detailViewModel.proDetailResp.getSoftwareCode()));
//                    detailViewModel.reqFavoritesList();
                    detailViewModel.reqFavoriteState(skuId);
                }else{
                    binding.rlRootLoading.setVisibility(View.GONE);
                    binding.loadingViewDetail.stopAnim();
                    if(detailViewModel.proDetailResp.getPutawayStatus() == 2){
                        binding.llUserAction.setVisibility(View.GONE);
                        binding.txtOffTheShelf.setVisibility(View.VISIBLE);
                        binding.rlDownload.setFocusable(false);
                        binding.rlShare.setFocusable(false);
                        binding.rlCollect.setFocusable(false);
                    }else{
                        binding.llUserAction.setVisibility(View.VISIBLE);
                        binding.txtOffTheShelf.setVisibility(View.GONE);
                        binding.rlDownload.setFocusable(true);
                        binding.rlShare.setFocusable(true);
                        binding.rlCollect.setFocusable(true);
                        binding.rlDownload.requestFocus();
                    }
                    binding.detailView.setVisibility(View.VISIBLE);
                    showLoadSuccess();
                    handleDetailUiUpdate(detailViewModel.proDetailResp);
                }

            } else if (LoadState.Failed == loadState) {
                binding.rlRootLoading.setVisibility(View.GONE);
                binding.loadingViewDetail.stopAnim();
              //  binding.detailView.setVisibility(View.GONE);
                showLoadFailed();
            }
        });

        detailViewModel.mPublishState.observe(this, loadState -> {
            if(LoadState.Loading == loadState) {
                binding.rlRootLoading.setVisibility(View.VISIBLE);
            } else if (LoadState.Success == loadState) {
                binding.detailView.setVisibility(View.VISIBLE);
                binding.rlRootLoading.setVisibility(View.GONE);
                binding.loadingViewDetail.stopAnim();
                binding.rlDownload.requestFocus();
                showLoadSuccess();
                handleDetailUiUpdate(detailViewModel.proDetailResp);

                PublishResp publishResp = detailViewModel.publishResp;
                if ((publishResp != null) && (publishResp.getSoftwareInfo() != null)) {
                    isProductRelease = true;
                    currentFileId = publishResp.getSoftwareInfo().getSoftwareCode();
                    lastVersion = publishResp.getSoftwareVersion();

                    String fileSize = FileUtils.FormatFileSizeNoUnit(Long.parseLong(publishResp.getPackageSize()));
                    binding.downloadPro.setText(String.format("??????(%sM)", fileSize));

                    DownloadInfo dbDownloadInfo = CareController.instance.getDownloadInfoByFileId(currentFileId);
                    if (dbDownloadInfo != null) {
                        if (dbDownloadInfo.status == DownloadInfo.STATUS_SUCCESS) {
                            binding.downloadIcon.setImageResource(R.drawable.img_experience_select);
                            binding.downloadPro.setText("????????????");
                            if (!dbDownloadInfo.version.equals(lastVersion)) {
                                detailViewModel.getUpgradeVersionInfo(new UpgradeReq(dbDownloadInfo.version, publishResp.getSoftwareInfo().getSoftwareCode()));
                            }
                        } else if (dbDownloadInfo.status == DownloadInfo.STATUS_STOPPED) {
                            binding.downloadIcon.setImageResource(R.drawable.img_continue_select);
                            binding.downloadPro.setText("??????");
                            if (!dbDownloadInfo.version.equals(lastVersion)) {
                                detailViewModel.getUpgradeVersionInfo(new UpgradeReq(dbDownloadInfo.version, publishResp.getSoftwareInfo().getSoftwareCode()));
                            }
                        } else if (dbDownloadInfo.status == DownloadInfo.STATUS_LOADING) {
                            binding.downloadIcon.setImageResource(R.drawable.img_downloading_select);
                            binding.downloadPro.setText("?????????");
                        }else if (dbDownloadInfo.status == DownloadInfo.STATUS_PENDING) {
                            binding.downloadIcon.setImageResource(R.drawable.img_wait_select);
                            binding.downloadPro.setText("?????????");
                        }
                    }
                } else {
                    isProductRelease = false;
                }
            } else if (LoadState.Failed == loadState) {
                binding.rlRootLoading.setVisibility(View.GONE);
                binding.loadingViewDetail.stopAnim();
               // binding.detailView.setVisibility(View.GONE);
                showLoadFailed();
            }
        });

        detailViewModel.mUpgradeState.observe(this, loadState -> {
            if (LoadState.Success == loadState) {
                if(detailViewModel.upgradeResp != null){
                    if(detailViewModel.upgradeResp.isForcible()){
                        String fileSize = FileUtils.FormatFileSizeNoUnit(Long.parseLong(detailViewModel.upgradeResp.getPackageSize()));
                        binding.downloadIcon.setImageResource(R.drawable.img_download_select);
                        binding.downloadPro.setText(String.format("??????(%sM)", fileSize));
                    }
                }
            }
        });

        detailViewModel.mCollectListState.observe(this, loadState -> {
            if (LoadState.Success == loadState) {
                isAddCollected = true;
                binding.imgCollect.setImageResource(R.mipmap.icon_collect_red);
//                List<FavoritesResp> favoritesRespList = detailViewModel.favoritesList;
//                if ((favoritesRespList != null) && (favoritesRespList.size() > 0)) {
//                    for (int i = 0; i < favoritesRespList.size(); i++) {
//                        FavoritesResp favoritesResp = favoritesRespList.get(i);
//                        if (skuId.equals(favoritesResp.objId)) {
//                            favoriteId = favoritesResp.favoriteId;
//                            isAddCollected = true;
//                            binding.imgCollect.setImageResource(R.mipmap.icon_collect_red);
//                            break;
//                        }
//                    }
//                }
            } else if (LoadState.Failed == loadState) {
                // showToast("????????????????????????");
            }
            isRequest=false;
        });

        detailViewModel.mAddCollectState.observe(this, loadState -> {
            if (LoadState.Success == loadState) {
                if (detailViewModel.collectResp != null) {
//                    favoriteId = detailViewModel.collectResp.getFavoriteId();
                    isAddCollected = true;
                    binding.imgCollect.setImageResource(R.mipmap.icon_collect_red);
                    showToast("??????????????????");
                } else {
                    showToast("??????????????????");
                }


            } else if (LoadState.Failed == loadState) {
                isAddCollected = false;
                showToast("??????????????????");
            }
            isRequest=false;
        });

        detailViewModel.mRemoveCollectState.observe(this, loadState -> {
            if (LoadState.Success == loadState) {
                isAddCollected = false;
                binding.imgCollect.setImageResource(R.drawable.img_collect_select);
                showToast("??????????????????");
            } else if (LoadState.Failed == loadState) {
                showToast("??????????????????");
            }
            isRequest=false;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(REQUEST_CODE_LOGIN == requestCode){
            detailViewModel.getProDetailInfo(new ProDetailReq(skuId));
        }
    }

    private void handleDetailUiUpdate(ProDetailResp proDetailResp){
        binding.productTitle.setText(proDetailResp.getProductTitle());
        binding.slogan.setText(proDetailResp.getSlogan());
        binding.played.setText(String.format("%s?????????", proDetailResp.getHeat()));

        List<ProDetailResp.SpecificationsInfo> specifications = proDetailResp.getSpecifications();
        for (int j = 0; j < specifications.size(); j++) {
            List<ProDetailResp.SpecificationsInfo.AttributesInfo> attributes = specifications.get(j).getAttributes();
            for (int i = 0; i < attributes.size(); i++) {
                ProDetailResp.SpecificationsInfo.AttributesInfo info = attributes.get(i);
                String name = info.getAttributeName();
                if (name.contains("??????")) {
                    binding.timeLength.setText(info.getAttributeValue());
                } else if (name.contains("??????")) {
                    binding.difficultyLevel.setText(info.getAttributeValue());
                }
            }
        }

        /*String htmlData = "<div style=\"color:#ffffff;\">" + proDetailResp.getDescription() + "</div>";
        binding.descriptionWeb.loadDataWithBaseURL(null, htmlData, "text/html", "utf-8", null);*/
        if (!TextUtils.isEmpty(proDetailResp.getDescription())){
            binding.description.setText(Html.fromHtml(proDetailResp.getDescription()));
        }

        GlideApp.with(this)
                .load(proDetailResp.getUseImgUrl())
                .apply(new RequestOptions()
                        .transform(new MultiTransformation<>(
                                new CenterCrop(),
                                new RoundedCorners(getResources().getDimensionPixelSize(R.dimen.src_dp_15))
                        ))
                        .placeholder(R.drawable.bg_shape_default))
                .into(binding.productImage);

        videoUrl = proDetailResp.getTutorial().getVideoUrl();

        readyPlayVideo();
    }

    private boolean handleAlgorithmLib(){
        if (downloadBinder != null) {
            List<AlgorithmInfoResp> algorithmInfoList = detailViewModel.publishResp.getRelevancyAlgorithmVersions();
            if(detailViewModel.upgradeResp != null ){
                algorithmInfoList = detailViewModel.upgradeResp.getRelevancyAlgorithmVersions();//?????????????????????
            }
            if(algorithmInfoList != null) {
                for (int i = 0; i < algorithmInfoList.size(); i++) {
                    AlgorithmInfoResp algorithmInfoResp = algorithmInfoList.get(i);
                    boolean successHandle = handleModelBin(algorithmInfoResp.relevancyModelVersions);
                    if(!successHandle){
                        return false;
                    }
                    DownloadInfo downloadAlgorithm = CareController.instance.getDownloadInfoByFileId(algorithmInfoResp.versionId);
                    if (downloadAlgorithm == null) {
                        boolean addOk = downloadBinder.startDownload(DownloadHelper.buildAlgorithmDownloadInfo(this, algorithmInfoResp));
                        if (!addOk) {
                            showToast("????????????");
                            return false;
                        }
                    } else if (downloadAlgorithm.status == DownloadInfo.STATUS_STOPPED) {
                        downloadBinder.startDownload(downloadAlgorithm.fileId);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private boolean handleModelBin(List<ModelInfoResp> relatedModelList){
        if(relatedModelList != null){
            for (int i = 0; i < relatedModelList.size(); i++) {
                ModelInfoResp modelInfoResp = relatedModelList.get(i);
                DownloadInfo downloadModel = CareController.instance.getDownloadInfoByFileId(modelInfoResp.versionId);
                if (downloadModel == null) {
                    boolean addOk = downloadBinder.startDownload(DownloadHelper.buildModelDownloadInfo(this, modelInfoResp));
                    if (!addOk) {
                        showToast("????????????");
                        return false;
                    }
                } else if (downloadModel.status == DownloadInfo.STATUS_STOPPED) {
                    downloadBinder.startDownload(downloadModel.fileId);
                }
            }
        }
        return true;
    }

    private void handleUpgrade() {
        if (detailViewModel.upgradeResp.isForcible()) {
            binding.downloadIcon.setImageResource(R.drawable.img_wait_select);
            binding.downloadPro.setText("?????????");
            DownloadInfo downloadInfo = DownloadHelper.buildUpgradeDownloadInfo(this, detailViewModel.proDetailResp, detailViewModel.publishResp, detailViewModel.upgradeResp);
            boolean success = downloadBinder.startDownload(downloadInfo);
            if(success){
                detailViewModel.upgradeResp = null;
            }
        } else {
            showUpgradeDialog();
        }
    }

    private void handleDownload() {
        if (downloadBinder != null) {
            DownloadInfo dbDownloadInfo = CareController.instance.getDownloadInfoByFileId(currentFileId);
            if (dbDownloadInfo == null) {
                binding.downloadIcon.setImageResource(R.drawable.img_wait_select);
                binding.downloadPro.setText("?????????");
                DownloadInfo downloadInfo = DownloadHelper.buildDownloadInfo(this, detailViewModel.proDetailResp, detailViewModel.publishResp);
                downloadBinder.startDownload(downloadInfo);
            } else {
                if (dbDownloadInfo.status == DownloadInfo.STATUS_LOADING || dbDownloadInfo.status == DownloadInfo.STATUS_PENDING) {
                    binding.downloadIcon.setImageResource(R.drawable.img_continue_select);
                    binding.downloadPro.setText("??????");
                    downloadBinder.pauseDownload(dbDownloadInfo.fileId);
                } else if (dbDownloadInfo.status == DownloadInfo.STATUS_STOPPED) {
                    binding.downloadIcon.setImageResource(R.drawable.img_downloading_select);
                    binding.downloadPro.setText("?????????");
                    downloadBinder.startDownload(dbDownloadInfo.fileId);
                } else {
                    if(LoadingPluginActivity.lastPluginPackageName != null
                            && !dbDownloadInfo.mainClassPath.equals(LoadingPluginActivity.lastPluginPackageName)){
                        removeRecentTask(LoadingPluginActivity.lastPluginPackageName);
                    }
                    startLoadingApplication();
                }
            }
        }
    }

    private void showUpgradeDialog() {
        upgradeDialog = new CustomNormalDialog(this, com.example.search.R.layout.layout_dialog);
        upgradeDialog.show();
        upgradeDialog.title.setText("???????????????????????????????????????");
        upgradeDialog.positive.setText("????????????");
        upgradeDialog.cancel.setText("??????");
        upgradeDialog.setOnClickListener(new CustomNormalDialog.OnClickListener() {
            @Override
            public void onConFirm() {
                binding.downloadIcon.setImageResource(R.drawable.img_wait_select);
                binding.downloadPro.setText("?????????");

                DownloadInfo downloadInfo = DownloadHelper.buildUpgradeDownloadInfo(DetailActivity.this, detailViewModel.proDetailResp, detailViewModel.publishResp, detailViewModel.upgradeResp);
                boolean success = downloadBinder.startDownload(downloadInfo);
                if(success){
                    detailViewModel.upgradeResp = null;
                }

            }

            @Override
            public void onCancel() {
                handleDownload();
            }
        });

    }

    private void showLoadSuccess() {
        binding.layoutNoNetwork.setVisibility(View.GONE);
        binding.layoutNormal.setVisibility(View.VISIBLE);

    }
    private void showLoadFailed() {
        binding.layoutNormal.setVisibility(View.GONE);
        binding.layoutNoNetwork.setVisibility(View.VISIBLE);
        binding.btnRefresh.requestFocus();
    }

    private void readyPlayVideo() {
        if (!TextUtils.isEmpty(videoUrl)) {
            HttpProxyCacheServer proxy = ZeeApplication.getProxy(this);
            if(!NetworkUtil.isNetworkAvailable(this) && !proxy.isCached(videoUrl)) {
                return;
            }
            binding.videoLayout.rlVideoRoot.setVisibility(View.VISIBLE);
            binding.loadProgress.setVisibility(View.VISIBLE);

            String proxyUrl = proxy.getProxyUrl(videoUrl);
            //binding.videoLayout.videoView.setVideoPath(proxyUrl);

            initVideo(proxyUrl);
        }
    }

    private void controlFocusAnimation(View v, boolean hasFocus) {
        if (hasFocus) {
            ViewCompat.animate(v).setDuration(200).scaleX(1.04f).scaleY(1.04f).start();
        } else {
            ViewCompat.animate(v).setDuration(200).scaleX(1f).scaleY(1f).start();
        }
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    /**
     * ?????????VideoView
     */
    private void initVideo(String url) {
        binding.videoLayout.timeSeekBar.setOnSeekBarChangeListener(onSeekBarChangeListener);
        binding.videoLayout.videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                // key = 0;
                //btnRestartPlay.setVisibility(View.VISIBLE);
                //layFinishBg.setVisibility(View.VISIBLE);
               /* if (videoUrl != null) {
                    binding.videoLayout.videoView.seekTo(0);
                    binding.videoLayout.videoView.start();
                }*/
            }
        });

        binding.videoLayout.videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {

            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                showToast("????????????");
                binding.loadProgress.setVisibility(View.GONE);
                return false;
            }
        });
        //videoView.getBufferPercentage();

        //????????????
        //final Uri uri = Uri.parse(url);
        binding.videoLayout.videoView.setVideoPath(url);
        // videoView.requestFocus();
        binding.videoLayout.videoView.setDrawingCacheEnabled(true);
        isVideoInited = true;
        binding.videoLayout.videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.i("test", "-----setOnPreparedListener------");
                if(isWindowOnFocus) {
                    Log.i("test", "-----setOnPreparedListener---xxxxxxxx---");
                    mp.setLooping(true);
                    int totalTime = binding.videoLayout.videoView.getDuration();//????????????????????????
                    binding.videoLayout.tvTotalTime.setText(stringForTime(totalTime));
                    // ???????????????????????????????????????
                    handler.postDelayed(runnable, 0);
                    binding.videoLayout.timeSeekBar.setMax(binding.videoLayout.videoView.getDuration());
                    //??????????????????,??????????????????????????????
                    binding.videoLayout.videoView.start();
                    if(binding.videoLayout.btnPlayOrPause.getVisibility() == View.VISIBLE) {
                        timeGone();
                    }
                    mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                        @Override
                        public boolean onInfo(MediaPlayer mp, int what, int extra) {
                            //?????????????????????
                            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                binding.videoLayout.videoView.setBackgroundColor(Color.TRANSPARENT);
                                binding.loadProgress.setVisibility(View.GONE);
                                binding.productImage.setVisibility(View.GONE);
                            }

                            return true;
                        }
                    });

                }

            }
        });
    }

    /**
     * ???????????????  ??????????????????
     *
     * @param isPlay ??????/????????????
     * @param keys 1 ????????????
     */
    private void isVideoPlay(boolean isPlay, int keys) {
        switch (keys) {
            case 0:
                if (isPlay) {//??????
                    binding.videoLayout.btnPlayOrPause.setBackgroundResource(R.mipmap.icon_player);
                    binding.videoLayout.btnPlayOrPause.setVisibility(View.VISIBLE);
                    binding.videoLayout.videoView.pause();
                } else {//????????????
                    binding.videoLayout.btnPlayOrPause.setBackgroundResource(R.mipmap.icon_pause);
                    binding.videoLayout.btnPlayOrPause.setVisibility(View.VISIBLE);
                    // ???????????????????????????????????????
                    handler.postDelayed(runnable, 0);
                    binding.videoLayout.videoView.start();
                    binding.videoLayout.timeSeekBar.setMax(binding.videoLayout.videoView.getDuration());
                    timeGone();
                }
                break;
            case 1://????????????
                //   initVideo("1");
                if (videoUrl != null) {
                    Log.i("dddttt", "????????????");
                    initVideo(videoUrl);
                }
                binding.videoLayout.btnRestartPlay.setVisibility(View.GONE);
                binding.videoLayout.layFinishBg.setVisibility(View.GONE);
                key = 0;
                break;
        }

    }

    /**
     * ????????????
     */
    private void timeGone() {
        handler.postDelayed(() -> binding.videoLayout.btnPlayOrPause.setVisibility(View.INVISIBLE), 1500);
    }

    /**
     * ???????????????
     */
    private final SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        // ???????????????????????????????????????
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // ??????????????????????????????
            int progress = seekBar.getProgress();
            if (binding.videoLayout.videoView.isPlaying()) {
                // ???????????????????????????
                binding.videoLayout.videoView.seekTo(progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {
        /*    if (binding.loadProgress.getVisibility() == View.VISIBLE) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        binding.loadProgress.setVisibility(View.GONE);
                        binding.productImage.setVisibility(View.GONE);
                    }
                },500);

            }*/
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:     //?????????enter
            case KeyEvent.KEYCODE_DPAD_CENTER:
                //????????????????????????????????????????????????????????????
                if (binding.productImage.getVisibility()==View.GONE){
                    isVideoPlay(binding.videoLayout.videoView.isPlaying(), key);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT: //?????????
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:  //?????????
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean isVideoInited = false;
    @Override
    protected void onPause() {
        isClickEnable = false;
        if (binding.videoLayout.videoView.isPlaying()){
            binding.videoLayout.videoView.pause();
            binding.productImage.setVisibility(View.VISIBLE);
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        isClickEnable = true;
        super.onResume();
        if(binding.videoLayout.rlVideoRoot.getVisibility() == View.VISIBLE && isVideoInited){
            binding.videoLayout.videoView.resume();
        }
        Log.i("test", "-----onResume------");
    }

    boolean isWindowOnFocus = true;
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        isWindowOnFocus = hasFocus;
        Log.i("test", "-----onWindowFocusChanged------" + hasFocus);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if ((downloadBinder != null) && (serviceConnection != null)) {
            downloadBinder.unRegisterDownloadListener(downloadListener);
            unbindService(serviceConnection);
        }

        if (zeeManager != null) {
            unbindManagerService();
        }

        if (upgradeDialog != null) {
            if (upgradeDialog.isShowing()) {
                upgradeDialog.dismiss();
            }
            upgradeDialog = null;
        }

        if (binding.videoLayout.videoView!=null){
            binding.videoLayout.videoView.setOnPreparedListener(null);
            binding.videoLayout.videoView.setOnCompletionListener(null);
            binding.videoLayout.videoView.setOnErrorListener(null);
            binding.videoLayout.videoView.destroyDrawingCache();
            binding.videoLayout.rlVideoRoot.removeAllViews();
        }

        super.onDestroy();
    }

    private void startLoadingApplication() {
        Intent intent = new Intent();
        AkSkResp akSkResp = UserCenterUtil.getAkSkInfo();
        if(akSkResp != null) {
            intent.putExtra(BaseConstants.EXTRA_AUTH_AK_CODE, akSkResp.akCode);
            intent.putExtra(BaseConstants.EXTRA_AUTH_SK_CODE, akSkResp.skCode);
            intent.putExtra(BaseConstants.EXTRA_PLUGIN_NAME, currentFileId);
            intent.setClass(this, LoadingPluginActivity.class);
            startActivity(intent);
        }
    }

    private void bindService() {
        Intent bindIntent = new Intent(this.getApplicationContext(), DownloadService.class);
        bindService(bindIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    private IZeeManager zeeManager = null;
    public void bindManagerService() {
        Intent bindIntent = new Intent(BaseConstants.MANAGER_SERVICE_ACTION);
        bindIntent.setPackage(BaseConstants.MANAGER_PACKAGE_NAME);
        bindService(bindIntent, managerServiceConnection, BIND_AUTO_CREATE);
    }

    public void unbindManagerService() {
        unbindService(managerServiceConnection);
    }

    private final ServiceConnection managerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "onServiceConnected()");
            zeeManager = IZeeManager.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "onServiceDisconnected()");
            zeeManager = null;
        }
    };

    public void removeRecentTask(String packageName){
        if(zeeManager != null){
            try {
                zeeManager.removeRecentTask(packageName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void handleRemoveAllRecentTasks(){
        if(zeeManager != null){
            try {
                String excludePackageName = getPackageName() + "," + BaseConstants.MANAGER_PACKAGE_NAME;
                zeeManager.removeAllRecentTasks(excludePackageName);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}
