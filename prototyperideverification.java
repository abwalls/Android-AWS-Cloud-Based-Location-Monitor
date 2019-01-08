package com.iamaws.hitchcoin.prototyperideverification;

//Android Import Statements
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

//AWS Import Statements
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.mobile.client.AWSStartupHandler;
import com.amazonaws.mobile.client.AWSStartupResult;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.pinpoint.PinpointManager;
import com.amazonaws.mobileconnectors.pinpoint.PinpointConfiguration;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;

//Lambda Imports
import com.amazonaws.mobile.api.idjb8thb7njf.RvsDynamoDBMobileHubClient;
import com.amazonaws.mobile.auth.core.IdentityManager;
import com.amazonaws.mobile.config.AWSConfiguration;
import com.amazonaws.mobileconnectors.apigateway.ApiClientFactory;
import com.amazonaws.mobileconnectors.apigateway.ApiRequest;
import com.amazonaws.mobileconnectors.apigateway.ApiResponse;
import com.amazonaws.util.IOUtils;
import com.amazonaws.util.StringUtils;
import java.io.InputStream;
import com.amazonaws.http.HttpMethodName;
import java.util.HashMap;
import java.util.Map;


//Java Import Statements
import java.util.Calendar;


public class MainActivity extends AppCompatActivity {
    public static PinpointManager pinpointManager;
    private FusedLocationProviderClient mFusedLocationClient;
    DynamoDBMapper dynamoDBMapper;
    boolean rideStarted = false;
    double rideId,startLat,startLong,endLat,endLong;
    String userId,startDate,endDate;

    //Lambda
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private RvsDynamoDBMobileHubClient apiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},2);
        }

        AWSMobileClient.getInstance().initialize(this, new AWSStartupHandler() {
            @Override
            public void onComplete(AWSStartupResult awsStartupResult) {
                //Log.d("YourMainActivity", "AWSMobileClient is instantiated and you are connected to AWS!");
            }
        }).execute();
        PinpointConfiguration config = new PinpointConfiguration(
                MainActivity.this,
                AWSMobileClient.getInstance().getCredentialsProvider(),
                AWSMobileClient.getInstance().getConfiguration()
        );
        pinpointManager = new PinpointManager(config);
        pinpointManager.getSessionClient().startSession();

        // Activates Location Play Service
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        //Lambda Function #1 API
        // Create the client
        apiClient = new ApiClientFactory()
                .credentialsProvider(AWSMobileClient.getInstance().getCredentialsProvider())
                .build(RvsDynamoDBMobileHubClient.class);

        // Instantiate a AmazonDynamoDBMapperClient
        //AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient(AWSMobileClient.getInstance().getCredentialsProvider());
        //this.dynamoDBMapper = DynamoDBMapper.builder()
                //.dynamoDBClient(dynamoDBClient)
                //.awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                //.build();

        pinpointManager.getAnalyticsClient().submitEvents();
        pinpointManager.getSessionClient().stopSession();
    }

    public void callCloudLogic(String sD,String sLat,String sLon,String eD,String eLat,String eLon) {

        //Get rideId and userId from edittext fields on application
        EditText rid = (EditText)findViewById(R.id.editText);
        EditText uid = (EditText)findViewById(R.id.editText2);
        String rideId = rid.getText().toString();
        String userId = uid.getText().toString();

        // Create components of api request
        final String method = "PUT";
        final String path = "/items";
        final String body = "";
        final byte[] content = body.getBytes(StringUtils.UTF8);
        final Map parameters = new HashMap<>();
        parameters.put("rideId", rideId);
        parameters.put("userId", userId);
        parameters.put("startDate",sD);
        parameters.put("startLat",sLat);
        parameters.put("startLong",sLon);
        parameters.put("endDate",eD);
        parameters.put("endLat",eLat);
        parameters.put("endLong",eLon);
        final Map headers = new HashMap<>();

        // Use components to create the api request
        ApiRequest localRequest =
                new ApiRequest(apiClient.getClass().getSimpleName())
                        .withPath(path)
                        .withHttpMethod(HttpMethodName.valueOf(method))
                        .withHeaders(headers)
                        .addHeader("Content-Type", "application/json")
                        .withParameters(parameters);

        // Only set body if it has content.
        if (body.length() > 0) {
            localRequest = localRequest
                    .addHeader("Content-Length", String.valueOf(content.length))
                    .withBody(content);
        }

        final ApiRequest request = localRequest;

        // Make network call on background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                apiClient.execute(request);
            }
        }).start();
    }

    public void onStartButton(View v){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},2);
        }
        rideStarted = true;
        mFusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    String userLat = Double.toString(location.getLatitude());
                    String userLong = Double.toString(location.getLongitude());
                    String coordinateToast = "Lat: " + userLat + " Long: " + userLong;
                    Toast myToast = Toast.makeText(getApplicationContext(),coordinateToast, Toast.LENGTH_LONG);
                    myToast.show();
                    startDate = String.valueOf(Calendar.getInstance().getTime());
                    startLat = location.getLatitude();
                    startLong = location.getLongitude();
                }
            }
        });
    }

    public void onEndButton(View v) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        }
        if (rideStarted) {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        String userLat = Double.toString(location.getLatitude());
                        String userLong = Double.toString(location.getLongitude());
                        String coordinateToast = "Lat: " + userLat + " Long: " + userLong;
                        Toast myToast = Toast.makeText(getApplicationContext(), coordinateToast, Toast.LENGTH_LONG);
                        myToast.show();
                        endDate = String.valueOf(Calendar.getInstance().getTime());
                        endLat = location.getLatitude();
                        endLong = location.getLongitude();
                        callCloudLogic(startDate,Double.toString(startLat),Double.toString(startLong),endDate,userLat,userLong);
                    }
                }
            });
        }
        else {
            Toast badEndClick = Toast.makeText(getApplicationContext(),"Must Push Start Button First", Toast.LENGTH_LONG);
            badEndClick.show();
        }
    }
}
