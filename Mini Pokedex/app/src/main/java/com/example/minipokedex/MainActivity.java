package com.example.minipokedex;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Dictionary;
import java.util.Hashtable;

public class MainActivity extends AppCompatActivity {

    private String TAG = MainActivity.class.getSimpleName();
    private ImageView imageView;
    private Bitmap bitmap;
    private TextView resultDisplayTV;
    private Button chooseImage, scanImage;
    private Uri uri;
    private Interpreter tfliteInterpreter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Getting ID's
        imageView = findViewById(R.id.text_image);
        resultDisplayTV = findViewById(R.id.results_tv);
        chooseImage = findViewById(R.id.add_img_btn);
        scanImage = findViewById(R.id.scan_image_btn);

        // Choose Img
        chooseImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    if(ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                    {
                        ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);

                    } else {

                        bringImagePicker();
                    }
                } else {

                    bringImagePicker();
                }
            }
        });

        try{
            tfliteInterpreter = new Interpreter(loadModelFile());
            Log.d(TAG,"Model Loaded");
        } catch(Exception ex){
            ex.printStackTrace();
            Log.d(TAG,"Model Not Loaded: "+ex.getMessage());
        }

        // Search Code
        scanImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resultDisplayTV.setText("");

                // Getting bitmap from Imageview
                BitmapDrawable drawable = (BitmapDrawable) imageView.getDrawable();
                bitmap = drawable.getBitmap();

                runImageRecoginition();

            }
        });


    }

    private void runImageRecoginition() {

        try {
            float[][][][] inputValue = new float[1][224][224][3];
            inputValue[0] = process(bitmap);
            Log.d("xlr8","Success in converting bitmap to rgb img");


            float[][] outputValue = new float[1][3];


            // Run inference passing the input shape and getting the output shape
            tfliteInterpreter.run(inputValue,outputValue);

            Dictionary<Integer,String> label_dict = new Hashtable<>();
            label_dict.put(0,"Mewoth");
            label_dict.put(1,"Pikachu");
            label_dict.put(2,"Bulbasaur");

            Dictionary<Integer,Float> result_dict = new Hashtable<>();



            float output[] = new float[3];
            output = outputValue[0];

            String res = "";
            int indx = 0;
            float prob_val = -1.0f;
            int pok_indx = -1;

            for(float p : output){
                res += p+" ";

                result_dict.put(indx,p);
                if(p > prob_val){
                    prob_val = p;
                    pok_indx = indx;
                }
                indx++;
            }

            String displayResult = "Predictions :\n";
            for(int i=0; i<3; i++){
                displayResult+=label_dict.get(i)+" : "+result_dict.get(i)+"\n";
            }

            displayResult+="\nResult : "+label_dict.get(pok_indx);

            resultDisplayTV.setText(displayResult);


            Log.d(TAG +"_res:",res);


            //{0:"Mewoth",1:"Pikachu",2:"Bulbasaur"};




        }catch (Exception e){
            Log.d(TAG,"Error: "+e.getMessage());
        }

    }

    public float[][][] process(Bitmap bitmap) {
        final int picw = 224;
        final int pich = 224;
        int[] pix = new int[picw * pich];
        float[][][] RGB_IMG = new float[picw][pich][3];
        bitmap.getPixels(pix, 0, picw, 0, 0, picw, pich);

        float R, G, B,Y;

        for (int y = 0; y < pich; y++){
            for (int x = 0; x < picw; x++)
            {
                int index = y * picw + x;
                R = (pix[index] >> 16) & 0xff;     //bitwise shifting
                G = (pix[index] >> 8) & 0xff;
                B = pix[index] & 0xff;

                RGB_IMG[x][y][0] = R;
                RGB_IMG[x][y][1] = G;
                RGB_IMG[x][y][2] = B;

                //R,G.B - Red, Green, Blue
                //to restore the values after RGB modification, use
                //next statement
                //pix[index] = 0xff000000 | (R << 16) | (G << 8) | B;
            }}

        return RGB_IMG;
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        // Open the model using an input stream, and memory map it to load
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd("mini_pokemon_mobilenetv2.tflite");
        FileInputStream inputStream = new FileInputStream((fileDescriptor.getFileDescriptor()));
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void bringImagePicker() {
        CropImage.activity()
                .setGuidelines(CropImageView.Guidelines.ON).setAspectRatio(1,1).setRequestedSize(224,224)
                .setCropShape(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? CropImageView.CropShape.RECTANGLE : CropImageView.CropShape.OVAL)
                .start(MainActivity.this);
    }

    private void getIMGSize(Uri uri){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(new File(uri.getPath()).getAbsolutePath(), options);
        int imageHeight = options.outHeight;
        int imageWidth = options.outWidth;
        resultDisplayTV.setText("Img H: "+imageHeight+"\nImg W: "+imageWidth);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {

                uri = result.getUri();
                resultDisplayTV.setText("");
                imageView.setImageURI(result.getUri());


            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {

                Exception error = result.getError();
                resultDisplayTV.setText("Error Loading Image: " + error);

            }
        }
    }
}
