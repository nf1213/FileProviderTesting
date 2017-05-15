package com.github.nf1213.fileprovidertesting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static android.os.Environment.DIRECTORY_DCIM;
import static android.provider.MediaStore.ACTION_IMAGE_CAPTURE;
import static android.provider.MediaStore.ACTION_VIDEO_CAPTURE;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REQUEST_CODE_TAKE_PHOTO_VIDEO = 3;
    private static final int REQUEST_CODE_OPEN_DOCUMENT = 4;

    private static final int REQUEST_PERMISSION_TAKE_PICTURE = 5;
    private static final int REQUEST_PERMISSION_CHOOSE_PICTURE = 6;
    private static final int REQUEST_PERMISSION_TAKE_VIDEO = 7;
    private static final int REQUEST_PERMISSION_CHOOSE_VIDEO = 8;

    private static final int IMAGE_TYPE = 0;
    private static final int VIDEO_TYPE = 1;

    private TextView textView;
    private ImageView imageView;
    private Uri mFileUri;
    private File mFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = (TextView) findViewById(R.id.my_text_view);
        imageView = (ImageView) findViewById(R.id.image);
        findViewById(R.id.action_image_capture).setOnClickListener(this);
        findViewById(R.id.action_choose_image).setOnClickListener(this);
        findViewById(R.id.action_take_video).setOnClickListener(this);
        findViewById(R.id.action_choose_video).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        mFile = null;
        mFileUri = null;

        boolean hasStoragePermission = Build.VERSION.SDK_INT < 23 || (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED);
        int requestCode = -1;

        switch (v.getId()) {
            case R.id.action_image_capture:
                if (hasStoragePermission) captureIntent(IMAGE_TYPE);
                else requestCode = REQUEST_PERMISSION_TAKE_PICTURE;
                break;
            case R.id.action_choose_image:
                if (hasStoragePermission) chooseIntent(IMAGE_TYPE);
                else requestCode = REQUEST_PERMISSION_CHOOSE_PICTURE;
                break;
            case R.id.action_take_video:
                if (hasStoragePermission) captureIntent(VIDEO_TYPE);
                else requestCode = REQUEST_PERMISSION_TAKE_PICTURE;
                break;
            case R.id.action_choose_video:
                if (hasStoragePermission) chooseIntent(VIDEO_TYPE);
                else requestCode = REQUEST_PERMISSION_CHOOSE_VIDEO;
                break;
        }

        if (requestCode > 0) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
        }
    }

    private void captureIntent(int type) {
        String action = type == VIDEO_TYPE ? ACTION_VIDEO_CAPTURE : ACTION_IMAGE_CAPTURE;
        Intent captureIntent = new Intent(action);
        if (captureIntent.resolveActivity(getPackageManager()) != null) {
            mFileUri = getNewFileUri(this, type);
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mFileUri);
            List<ResolveInfo> camerasList = getPackageManager().queryIntentActivities(captureIntent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : camerasList) {
                String packageName = resolveInfo.activityInfo.packageName;
                grantUriPermission(packageName, mFileUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (type == VIDEO_TYPE) {
                // also allow playback apps to view the video
                Intent playVideoIntent = new Intent(Intent.ACTION_VIEW);
                playVideoIntent.setDataAndType(mFileUri, "video/*");

                List<ResolveInfo> videoPlayersList = getPackageManager().queryIntentActivities(playVideoIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : videoPlayersList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    grantUriPermission(packageName, mFileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
            startActivityForResult(captureIntent, REQUEST_CODE_TAKE_PHOTO_VIDEO);
        }
    }

    private void chooseIntent(int type) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            openDocumentIntent(type);
        } else {
            galleryIntent(type);
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private void openDocumentIntent(int type) {

        String mimeType = type == VIDEO_TYPE ? "video/*" : "image/*";

        // ACTION_OPEN_DOCUMENT is the intent to chooseIntent a file via the system's file
        // browser.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        // Filter to show only images, using the image MIME data type.
        // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
        // To search for all documents available via installed storage providers,
        // it would be "*/*".
        intent.setType(mimeType);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT);
    }

    private void galleryIntent(int type) {
        String mimeType = type == VIDEO_TYPE ? "video/*" : "image/*";

        // Implicitly allow the user to select a particular kind of data
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        // The MIME data type filter
        intent.setType(mimeType);
        // Only return URIs that can be opened with ContentResolver
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK ) {
            switch (requestCode) {
                case REQUEST_CODE_TAKE_PHOTO_VIDEO: {
                    mediaScan();
                    break;
                }
                case REQUEST_CODE_OPEN_DOCUMENT: {
                    mFileUri = data.getData();
                    try {
                        String path = getPath(mFileUri);
                        if (!TextUtils.isEmpty(path)) {
                            mFile = new File(path);
                        }
                    } catch (URISyntaxException e) {
                        Log.v("MainActivity", "UGH WHYYYY");
                    }
                    break;
                }
            }
            if (mFile != null && mFile.exists()) {
                textView.setText(mFile.getAbsolutePath());
                imageView.setImageURI(mFileUri);
            } else {
                textView.setText("ERROR");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) return;

        switch (requestCode) {
            case REQUEST_PERMISSION_TAKE_PICTURE:
                captureIntent(IMAGE_TYPE);
                break;
            case REQUEST_PERMISSION_CHOOSE_PICTURE:
                chooseIntent(IMAGE_TYPE);
                break;
            case REQUEST_PERMISSION_TAKE_VIDEO:
                captureIntent(VIDEO_TYPE);
                break;
            case REQUEST_PERMISSION_CHOOSE_VIDEO:
                chooseIntent(VIDEO_TYPE);
                break;
        }
    }

    //region https://github.com/awslabs/aws-sdk-android-samples/blob/master/S3TransferUtilitySample/src/com/amazonaws/demo/s3transferutility/UploadActivity.java
    @SuppressLint("NewApi")
    private String getPath(Uri uri) throws URISyntaxException {
        final boolean needToCheckUri = Build.VERSION.SDK_INT >= 19;
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        // deal with different Uris.
        if (needToCheckUri && DocumentsContract.isDocumentUri(getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[] {
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {
                    MediaStore.Images.Media.DATA
            };
            Cursor cursor = null;
            try {
                cursor = getContentResolver()
                        .query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }
    //endregion

    private Uri getNewFileUri(Context context, int type) {
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(DIRECTORY_DCIM), "Camera");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.v("MainActivity", "failed to create directory");
                return null;
            }
        }
        String prefix = type == IMAGE_TYPE ? "IMG_" : "";
        String suffix = type == IMAGE_TYPE ? ".png" : ".mp4";
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        mFile = new File(mediaStorageDir.getPath() + File.separator + prefix + timeStamp + suffix);
        Uri uri = FileProvider.getUriForFile(context, "com.example.fileprovider", mFile);
        Log.v("MainActivity", uri.toString());
        return uri;
    }

    private void mediaScan() {
        if (mFile == null || !mFile.exists()) return;
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(mFile);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }
}
