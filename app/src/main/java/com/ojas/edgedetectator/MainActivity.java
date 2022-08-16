package com.ojas.edgedetectator;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final int imageRequest = 1;

    private static boolean initOpenCV = false;
    private Uri imageUri;
    String alertUrl = "";
    boolean doubleBackToExitPressedOnce = false;

    static {
        initOpenCV = OpenCVLoader.initDebug();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ImageButton camera = findViewById(R.id.camera);
        ImageButton gallery = findViewById(R.id.gallery);
        ImageView originalImg = findViewById(R.id.original);
        ImageView processedImg = findViewById(R.id.processed);
        String originalBitmap = getIntent().getStringExtra("original");
        String processedBitmap = getIntent().getStringExtra("processed");

        if (originalBitmap != null) originalImg.setImageBitmap(getBitmapFromURL(originalBitmap));
        if (processedBitmap != null) processedImg.setImageBitmap(getBitmapFromURL(processedBitmap));

        gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFile();
            }
        });

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
//                cameraView.setCvCameraViewListener(MainActivity.this);
            }
        });
    }

    private void dispatchTakePictureIntent() {

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 123);
            Toast.makeText(this, "Need Permission to access the Camera", Toast.LENGTH_SHORT).show();
        } else {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            try {
                startActivityForResult(takePictureIntent, 10);
            } catch (ActivityNotFoundException ignored) {
            }
        }


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == imageRequest && resultCode == RESULT_OK) {
            imageUri = data.getData();
            uploadImage(imageUri);
        }
        if (requestCode == 10 && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
            String path = MediaStore.Images.Media.insertImage(MainActivity.this.getContentResolver(), imageBitmap, "" + System.currentTimeMillis(), null);
            imageUri = Uri.parse(path);
            uploadImage(imageUri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    public void onCameraViewStarted(int width, int height) {
    }

    @Override
    public void onCameraViewStopped() {
    }


    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat src = inputFrame.gray();
        Mat cannyEdges = new Mat();
        Imgproc.Canny(src, cannyEdges, 20, 80);
        return cannyEdges;
    }

    private Uri detectEdges(Uri uri) {
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(MainActivity.this.getContentResolver(), uri);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Mat rgba = new Mat();
        Utils.bitmapToMat(bitmap, rgba);

        Mat edges = new Mat(rgba.size(), CvType.CV_8UC1);
        Imgproc.cvtColor(rgba, edges, Imgproc.COLOR_RGB2GRAY, 4);
        Imgproc.Canny(edges, edges, 50, 100);

        Bitmap resultBitmap = Bitmap.createBitmap(edges.cols(), edges.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(edges, resultBitmap);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(this.getContentResolver(), resultBitmap, "" + System.currentTimeMillis(), null);

        return Uri.parse(path);
    }

    private void openFile() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, imageRequest);

    }

    private String getFileExtension(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        return mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri));
    }

    private void uploadImage(Uri imageUri) {
        Intent intent = getIntent();
        final String[] original = {""};
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("uploading");
        pd.setCanceledOnTouchOutside(false);
        pd.setCancelable(false);
        pd.show();


        if (imageUri != null) {
            final StorageReference fileRef = FirebaseStorage.getInstance().getReference().child("uploads").child(System.currentTimeMillis() + "." + getFileExtension(imageUri));
            fileRef.putFile(imageUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                    if (task.isSuccessful()) {
                        fileRef.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                original[0] = uri.toString();
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                pd.dismiss();
                                Toast.makeText(MainActivity.this, "Error : " + e.toString(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            });
            final StorageReference fileRef2 = FirebaseStorage.getInstance().getReference().child("uploads").child(System.currentTimeMillis() + "." + getFileExtension(imageUri));
            fileRef2.putFile(detectEdges(imageUri)).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                    if (task.isSuccessful()) {
                        fileRef2.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                String url = uri.toString();
                                pd.dismiss();
                                Toast.makeText(MainActivity.this, "upload successful", Toast.LENGTH_LONG).show();
                                StorageReference filePath = FirebaseStorage.getInstance().getReference().child("uploads");
                                sendFile(original[0], url);
                                Intent intent1 = new Intent(MainActivity.this, MainActivity.class);
                                intent1.putExtra("original", imageUri.toString());
                                intent1.putExtra("processed", detectEdges(imageUri).toString());
                                startActivityForResult(intent1, 3);
                                finish();


                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                pd.dismiss();
                                Toast.makeText(MainActivity.this, "Error : " + e.toString(), Toast.LENGTH_LONG).show();
                            }
                        });
                    } else {
                        pd.dismiss();
                        Toast.makeText(MainActivity.this, "Error : " + task.getException().toString(), Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            Toast.makeText(MainActivity.this, "Error : Image not selected", Toast.LENGTH_LONG).show();
        }
    }

    private void sendFile(String fileAddr, String processedFileAddr) {
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference().child("uploads").push();
        HashMap<String, Object> hashMap = new HashMap();
        hashMap.put("originalImage", fileAddr);
        hashMap.put("processedImage", processedFileAddr);
        hashMap.put("time", "" + System.currentTimeMillis());
        hashMap.put("key", reference.getKey());
        reference.setValue(hashMap);
    }

    public Bitmap getBitmapFromURL(String uri) {
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(MainActivity.this.getContentResolver(), Uri.parse(uri));

            return bitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            finishAffinity();
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    //    public void ShowDialog() {
//        final AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
//        View view = getLayoutInflater().inflate(R.layout.status_dialog, null);
//        Button save = view.findViewById(R.id.saveBtn);
//        final EditText enterStatus = view.findViewById(R.id.enterStatus);
//        alert.setView(view);
//        final AlertDialog alertDialog = alert.create();
//        alertDialog.setCanceledOnTouchOutside(true);
//
//        save.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                alertUrl = enterStatus.getText().toString();
//                DownloadImage(alertUrl);
//                alertDialog.dismiss();
//            }
//        });
//        alertDialog.show();
//    }
//
//    void DownloadImage(String ImageUrl) {
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
//                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 123);
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 123);
//            Toast.makeText(this, "Need Permission to access storage for Downloading Image", Toast.LENGTH_SHORT).show();
//        } else {
//            Toast.makeText(this, "Downloading Image", Toast.LENGTH_SHORT).show();
//            //Asynctask to create a thread to downlaod image in the background
//            new DownloadsImage().execute(ImageUrl);
//        }
//    }
//
//    class DownloadsImage extends AsyncTask<String, Void,Void> {
//
//        @Override
//        protected Void doInBackground(String... strings) {
//            URL url = null;
//            try {
//                url = new URL(strings[0]);
//            } catch (MalformedURLException e) {
//                e.printStackTrace();
//            }
//            Bitmap bm = null;
//            try {
//                bm =    BitmapFactory.decodeStream(url.openConnection().getInputStream());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//            //Create Path to save Image
//            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES+ "/EdgeDetector"); //Creates app specific folder
//
//            Log.d("PATHH : ", ""+path);
//            if(!path.exists()) {
//                path.mkdirs();
//            }
//
//            File imageFile = new File(path, String.valueOf(System.currentTimeMillis())+"."+getIntent().getStringExtra("Type"));
//            FileOutputStream out = null;
//            try {
//                out = new FileOutputStream(imageFile);
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            }
//            try{
//                bm.compress(Bitmap.CompressFormat.PNG, 100, out); // Compress Image
//                out.flush();
//                out.close();
//                // Tell the media scanner about the new file so that it is
//                // immediately available to the user.
//                MediaScannerConnection.scanFile(MainActivity.this,new String[] { imageFile.getAbsolutePath() }, null,new MediaScannerConnection.OnScanCompletedListener() {
//                    public void onScanCompleted(String path, Uri uri) {
//                        Log.d("OJAS : ", "URI : " +uri);
//
////                        uploadImage(uri);
//                    }
//
//
//                });
//            } catch(Exception e) {
//                Log.d("ERROR OJAS : ", "OJAS : "+ e );
//            }
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(Void aVoid) {
//            super.onPostExecute(aVoid);
//            Toast.makeText(MainActivity.this, "Downloaded", Toast.LENGTH_SHORT).show();
//        }
//    }

}