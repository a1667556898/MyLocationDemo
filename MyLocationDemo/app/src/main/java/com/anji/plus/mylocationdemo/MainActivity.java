package com.anji.plus.mylocationdemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.fence.GeoFence;
import com.amap.api.fence.GeoFenceClient;
import com.amap.api.fence.GeoFenceListener;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.location.DPoint;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CircleOptions;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.MyLocationStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private TextView tv;
    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    MapView mMapView = null;
    AMap aMap;
    //声明小蓝点图标
    MyLocationStyle myLocationStyle;
    //声明AMapLocationClientOption对象
    public AMapLocationClientOption mLocationOption = null;
    //实例化地理围栏客户端
    GeoFenceClient mGeoFenceClient = null;
    // 触发地理围栏的行为，默认为进入提醒

    private ArrayList<DPoint> dPoints;//创建地理围栏的中心点集合
    List<GeoFence> fenceList = new ArrayList<GeoFence>();//添加围栏成功后返回的围栏信息集合
    // 记录已经添加成功的围栏
    private HashMap<String, GeoFence> fenceMap = new HashMap<String, GeoFence>();
    //根据围栏id 记录每个围栏的状态
    private HashMap<String, Integer> fenceIdMap = new HashMap<>();
    //定义接收广播的action字符串
    public static final String GEOFENCE_BROADCAST_ACTION = "com.location.apis.geofencedemo.broadcast";
    // 当前的坐标点集合，主要用于进行地图的可视区域的缩放
    private LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    tv.setText("添加围栏成功");
                    Toast.makeText(getApplicationContext(), "添加围栏成功",
                            Toast.LENGTH_SHORT).show();
                    //开始画围栏
                    drawFence2Map();
                    break;
                case 1:
                    tv.setText("添加围栏失败");
                    int errorCode = msg.arg1;
                    Toast.makeText(getApplicationContext(), "添加围栏失败" + errorCode,
                            Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    break;
                case 3:
                    tv.setText("定位失败");
                    break;
                case 4:
                    //遍历map 初始化是否在围栏里
                    boolean isInFence = false;//默认false
                    for (Integer value : fenceIdMap.values()) {
                        if (value == GeoFence.STATUS_IN) {
                            isInFence = true;
                        }
                    }
                    if (isInFence) {
                        tv.setText("进入围栏");
                    } else {
                        tv.setText("离开围栏");
                    }
                    break;
                case 5:
                    tv.setText("离开围栏");
                    break;
                case 6:
                    tv.setText("停留围栏");
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.tv);
        //获取地图控件引用
        mMapView = (MapView) findViewById(R.id.map);
        //在activity执行onCreate时执行mMapView.onCreate(savedInstanceState)，创建地图
        mMapView.onCreate(savedInstanceState);
        //初始化地图控制器对象
        initMap();
        initLocation();
        //初始化电子围栏对象
        initGeofence();
    }

    Object lock = new Object();

    private void drawFence2Map() {
//        Log.i("sss", "添加围栏成功222");
        new Thread() {
            @Override
            public void run() {
                try {
                    synchronized (lock) {
                        if (null == fenceList || fenceList.isEmpty()) {
                            return;
                        }
                        for (GeoFence fence : fenceList) {
                            if (fenceMap.containsKey(fence.getFenceId())) {
                                continue;
                            }
                            drawFence(fence);
                            fenceMap.put(fence.getFenceId(), fence);
                        }
                    }
                } catch (Throwable e) {

                }
            }

        }.start();

    }

    private void drawFence(GeoFence fence) {
//        Log.i("sss","fenceList:"+fence.getCenter().getLatitude());
        LatLng center = new LatLng(fence.getCenter().getLatitude(),
                fence.getCenter().getLongitude());
        // 绘制一个圆形
        aMap.addCircle(new CircleOptions().center(center)
                .radius(fence.getRadius()).strokeColor(Const.STROKE_COLOR)
                .fillColor(Const.FILL_COLOR).strokeWidth(Const.STROKE_WIDTH));
        boundsBuilder.include(center);
        dPoints.clear();
    }


    private void initGeofence() {
        dPoints = new ArrayList<>();
        mGeoFenceClient = new GeoFenceClient(getApplicationContext());
        /**
         * 设置地理围栏的触发行为,默认为进入
         */
        mGeoFenceClient.setActivateAction(GeoFenceClient.GEOFENCE_IN | GeoFenceClient.GEOFENCE_OUT | GeoFenceClient.GEOFENCE_STAYED);
        //创建自定义围栏 多个围栏需要创建多次
        //Point：围栏中心点
        //radius：要创建的围栏半径 ，半径无限制，单位米
        //customId ：与围栏关联的自有业务Id
        //创建一个中心点坐标
        DPoint centerPoint = new DPoint();
        //设置中心点纬度
        centerPoint.setLatitude(31.286D);
        //设置中心点经度
        centerPoint.setLongitude(121.518D);
        dPoints.add(centerPoint);
        // 创建一个中心点坐标
        DPoint centerPoint1 = new DPoint();
        //设置中心点纬度
        centerPoint1.setLatitude(31.296D);
        //设置中心点经度
        centerPoint1.setLongitude(121.528D);
        dPoints.add(centerPoint1);
        // 创建一个中心点坐标
        DPoint centerPoint2 = new DPoint();
        //设置中心点纬度
        centerPoint2.setLatitude(31.306D);
        //设置中心点经度
        centerPoint2.setLongitude(121.538D);
        dPoints.add(centerPoint2);
        // 创建一个中心点坐标
        DPoint centerPoint3 = new DPoint();
        //设置中心点纬度
        centerPoint3.setLatitude(31.326D);
        //设置中心点经度
        centerPoint3.setLongitude(121.548D);
        dPoints.add(centerPoint3);
        // 创建一个中心点坐标
        DPoint centerPoint4 = new DPoint();
        //设置中心点纬度
        centerPoint4.setLatitude(31.316D);
        //设置中心点经度
        centerPoint4.setLongitude(121.558D);
        dPoints.add(centerPoint4);
        //动态添加中心点
        for (int i = 0; i < dPoints.size(); i++) {
            mGeoFenceClient.addGeoFence(dPoints.get(i), 100F, "自有业务Id" + i);
        }

        //创建回调监听
        GeoFenceListener fenceListenter = new GeoFenceListener() {

            @Override
            public void onGeoFenceCreateFinished(List<GeoFence> list, int errorCode, String customId) {
                Message msg = Message.obtain();
                if (errorCode == GeoFence.ADDGEOFENCE_SUCCESS) {//判断围栏是否创建成功
//                    Log.i("sss", "添加围栏成功111");
                    msg.obj = customId;
                    msg.what = 0;
                    fenceList.addAll(list);
                    //geoFenceList就是已经添加的围栏列表，可据此查看创建的围栏
                } else {
                    //geoFenceList就是已经添加的围栏列表
                    msg.arg1 = errorCode;
                    msg.what = 1;
                }
                handler.sendMessage(msg);

            }

        };
        //设置回调监听
        mGeoFenceClient.setGeoFenceListener(fenceListenter);
        //创建并设置PendingIntent
        mGeoFenceClient.createPendingIntent(GEOFENCE_BROADCAST_ACTION);
        IntentFilter filter = new IntentFilter(
                ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(GEOFENCE_BROADCAST_ACTION);
        registerReceiver(mGeoFenceReceiver, filter);
    }

    private void initMap() {
        if (aMap == null) {
            aMap = mMapView.getMap();
        }
        myLocationStyle = new MyLocationStyle();//初始化定位蓝点样式类
        myLocationStyle.interval(10000); //设置连续定位模式下的定位间隔
        myLocationStyle.strokeColor(Color.argb(0, 0, 0, 0));//设置定位蓝点精度圆圈的边框颜色的方法。
        myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));//设置定位蓝点精度圆圈的填充颜色的方法。
        myLocationStyle.strokeWidth(0);//设置定位蓝点精度圈的边框宽度的方法。
        BitmapDescriptor bitmapdescriptor = BitmapDescriptorFactory.fromResource(R.mipmap.gps_point);
        myLocationStyle.myLocationIcon(bitmapdescriptor);
        aMap.setMyLocationStyle(myLocationStyle);//设置定位蓝点的Style
        aMap.getUiSettings().setMyLocationButtonEnabled(true);//设置默认定位按钮是否显示，非必需设置。
        aMap.setMyLocationEnabled(true);// 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。

    }

    private void initLocation() {
        //初始化定位
        mLocationClient = new AMapLocationClient(getApplicationContext());
        //设置定位回调监听
        mLocationClient.setLocationListener(mAMapLocationListener);
        //初始化AMapLocationClientOption对象
        mLocationOption = new AMapLocationClientOption();

//设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
//设置定位间隔,单位毫秒,默认为2000ms，最低1000ms。
//        mLocationOption.setInterval(10000);
        //只定位一次
        mLocationOption.setOnceLocation(true);
//设置是否返回地址信息（默认返回地址信息）
        mLocationOption.setNeedAddress(true);
        //单位是毫秒，默认30000毫秒，建议超时时间不要低于8000毫秒。
        mLocationOption.setHttpTimeOut(20000);
        //给定位客户端对象设置定位参数
        mLocationClient.setLocationOption(mLocationOption);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //启动定位
        mLocationClient.startLocation();
    }

    //异步获取定位结果
    AMapLocationListener mAMapLocationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation amapLocation) {
            if (amapLocation != null) {
                if (amapLocation.getErrorCode() == 0) {
                    //解析定位结果
//                    Log.i("sss", "结果：" + amapLocation.getAddress());
                } else {
                    //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
                    Log.e("AmapError", "location Error, ErrCode:"
                            + amapLocation.getErrorCode() + ", errInfo:"
                            + amapLocation.getErrorInfo());
                }
            }
        }
    };
    //  地理围栏回调 进入 离开 停留
    private BroadcastReceiver mGeoFenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(GEOFENCE_BROADCAST_ACTION)) {
                //解析广播内容
                //获取Bundle
                Bundle bundle = intent.getExtras();
//获取围栏行为：
                int status = bundle.getInt(GeoFence.BUNDLE_KEY_FENCESTATUS);
//获取自定义的围栏标识：
                String customId = bundle.getString(GeoFence.BUNDLE_KEY_CUSTOMID);
//获取围栏ID:
                String fenceId = bundle.getString(GeoFence.BUNDLE_KEY_FENCEID);
//获取当前有触发的围栏对象：
                GeoFence fence = bundle.getParcelable(GeoFence.BUNDLE_KEY_FENCE);
                Log.i("sss", "获取围栏行为:" + status);
                Message msg = Message.obtain();
                //改变数据类型
                fenceIdMap.put(fenceId, status);
                switch (status) {
                    case GeoFence.STATUS_LOCFAIL:
                        Toast.makeText(getApplicationContext(), "定位失败",
                                Toast.LENGTH_SHORT).show();
                        msg.what = 3;
                        handler.sendMessage(msg);
                        break;
                    case GeoFence.STATUS_IN:
                        Toast.makeText(getApplicationContext(), "进入围栏",
                                Toast.LENGTH_SHORT).show();
                        msg.what = 4;
                        handler.sendMessage(msg);
                        break;
                    case GeoFence.STATUS_OUT:
                        Toast.makeText(getApplicationContext(), "离开围栏",
                                Toast.LENGTH_SHORT).show();
                        msg.what = 4;
                        handler.sendMessage(msg);
                        break;
                    case GeoFence.STATUS_STAYED:
                        msg.what = 4;
                        handler.sendMessage(msg);
                        Toast.makeText(getApplicationContext(), "停留在围栏内",
                                Toast.LENGTH_SHORT).show();

                        break;
                    default:
                        break;
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        if (mLocationClient != null) {
            mLocationClient.onDestroy();//销毁定位客户端，同时销毁本地定位服务。
        }
        if (null != mGeoFenceClient) {
            //会清除所有围栏
            mGeoFenceClient.removeGeoFence();
        }
        mMapView.onDestroy();
        try {
            unregisterReceiver(mGeoFenceReceiver);
        } catch (Throwable e) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        mMapView.onPause();

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mLocationClient != null) {
            mLocationClient.stopLocation();//停止定位后，本地定位服务并不会被销毁
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        mMapView.onSaveInstanceState(outState);
    }
}
