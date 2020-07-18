package com.forif.honsullife.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.forif.honsullife.R;
import com.forif.honsullife.model.Post;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.Objects;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

public class PublishPostActivity extends AppCompatActivity implements View.OnClickListener{

        //final statics
        private final static String TAG = "system";
        private final static int GET_GALLERY_IMAGE = 101;
        private final static int GET_CAMERA_IMAGE = 102;
        private final String DIRECTORY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/myCamera";

        //UI
        private ImageView mImageView;
        private EditText mTitle;
        private EditText mContent;
        private FloatingActionButton mUpload;
        private BottomAppBar mBottomAppBar;

        //Storage & DB
        private StorageReference mStorageRef;
        private DatabaseReference mDBRef;


        //Uri & Task & Toast
        private Uri mImageUrl;
        private Task<Uri> mUploadTask;
        private Toast mToast;

        /*1. 사진 업로드
         * 2. 사진 제목 작성
         * 3. 사진 설명 작성
         * 4. Firebase Database 에다가 업로드하기 */
        @Override
        protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_publish_post);
                Log.d(TAG, "onCreate: ");
                mImageView = findViewById(R.id.img_publish_post_image);
                mTitle = findViewById(R.id.et_publish_post_title);
                mContent = findViewById(R.id.et_publish_post_content);
                mUpload = findViewById(R.id.fab_publish_post);

                //Security
                ActivityCompat.requestPermissions(this,new String[]{CAMERA,WRITE_EXTERNAL_STORAGE},
                        PackageManager.PERMISSION_GRANTED);
                StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
                StrictMode.setVmPolicy(builder.build());

                mStorageRef = FirebaseStorage.getInstance().getReference("uploads");
                mDBRef = FirebaseDatabase.getInstance().getReference("uploads");

                mBottomAppBar = findViewById(R.id.publish_bottom_app_bar);
                setSupportActionBar(mBottomAppBar);

                mUpload.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
                int id = v.getId();
                switch (id){
                        case R.id.fab_publish_post:
                                upload();
                                break;
                }
        }

        //Creating App bar menu
        @Override
        public boolean onCreateOptionsMenu(Menu menu) {
                Log.d(TAG, "onCreateOptionsMenu: ");
                MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.publish_bottom_menu,menu);
                return true;

        }

        //Menu Item Selected
        @Override
        public boolean onOptionsItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()){
                        case R.id.menu_take_photo:
                                openCamera();
                                return true;

                        case R.id.menu_select_image:
                                selectImageOnGallery();
                                return true;
                }

                return super.onOptionsItemSelected(item);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
                super.onActivityResult(requestCode, resultCode, data);


                //Select Image in Gallery
                if(requestCode == GET_GALLERY_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null){
                        mImageUrl = data.getData();

                        Glide.with(PublishPostActivity.this)
                                .load(mImageUrl)
                                .centerCrop()
                                .into(mImageView);
                } else if (requestCode == GET_CAMERA_IMAGE && requestCode == RESULT_OK && data != null && data.getData() != null){
                        mImageUrl = data.getData();
                }

        }

        //User-defined Functions
        private void selectImageOnGallery(){
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent,GET_GALLERY_IMAGE);
        }

        private void openCamera(){

                String file = DIRECTORY + System.currentTimeMillis() + ".jpg";
                File newFile = new File(file);

                try{
                        newFile.createNewFile();
                } catch (Exception e){
                        e.printStackTrace();
                        Toast.makeText(this, "ERROR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }

                Uri outputFileUri = Uri.fromFile(newFile);
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT,outputFileUri);
                //intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(intent,GET_CAMERA_IMAGE);
        }

        private void upload(){
                if(mUploadTask != null){
                        if(mToast != null) mToast.cancel();
                        mToast = Toast.makeText(PublishPostActivity.this,"Uploading is on progress!",Toast.LENGTH_SHORT);
                        mToast.show();
                } else{
                        final String title = mTitle.getText().toString();
                        final String content = mContent.getText().toString();

                        final Timestamp timestamp = new Timestamp(System.currentTimeMillis());

                        if(title.trim().length() > 0 && content.trim().length() > 0){

                                if(mImageUrl != null){
                                        String fileName = System.currentTimeMillis() + "." + getFileExtenstion(mImageUrl);
                                        final StorageReference storageRef = mStorageRef.child(fileName.trim());

                                        mUploadTask = storageRef.putFile(mImageUrl)
                                                .continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                                                        @Override
                                                        public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                                                                if(task.isSuccessful()){
                                                                        return storageRef.getDownloadUrl();
                                                                } else{
                                                                        throw Objects.requireNonNull(task.getException());
                                                                }
                                                        }
                                                })
                                                .addOnCompleteListener(new OnCompleteListener<Uri>() {
                                                        @Override
                                                        public void onComplete(@NonNull Task<Uri> task) {
                                                                if(task.isSuccessful()){
                                                                        String uploadId = mDBRef.push().getKey();
                                                                        Uri downloadId = task.getResult();

                                                                        Post post = new Post(title,content,downloadId.toString(),timestamp.toString());
                                                                        post.setKey(uploadId);
                                                                        mDBRef.child(uploadId).setValue(post);
                                                                }
                                                        }
                                                })
                                                .addOnSuccessListener(new OnSuccessListener<Uri>() {
                                                        @Override
                                                        public void onSuccess(Uri uri) {
                                                                Intent intent = new Intent();
                                                                setResult(RESULT_OK,intent);
                                                                Toast.makeText(PublishPostActivity.this, "Uploading...", Toast.LENGTH_SHORT).show();
                                                                finish();
                                                        }
                                                })
                                                .addOnFailureListener(new OnFailureListener() {
                                                        @Override
                                                        public void onFailure(@NonNull Exception e) {
                                                                Toast.makeText(PublishPostActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                                                        }
                                                });
                                } else {
                                        Toast.makeText(this, "You have to upload at least one image!", Toast.LENGTH_SHORT).show();
                                }
                        } else {
                                Toast.makeText(this, "You have to type at list one character on title or content!", Toast.LENGTH_SHORT).show();
                        }
                }
        }

        private String getFileExtenstion(Uri uri){
                ContentResolver cR = getContentResolver();
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                return mime.getExtensionFromMimeType(cR.getType(uri));
        }

}