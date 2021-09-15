package com.image.imagecapture;

import android.view.View;
import android.widget.ImageView;

/**
 * Created by Charles Raj I on 07/09/21.
 *
 * @author Charles Raj I
 */
public interface CameraListener {


    void takePicture(View view);


    void takeVideo(View view);

    void submitClick(View view);

    void changeCamera(View view);

    void closeBackToCam(View view);

    void flashOnOff(View view, ImageView imageView);
}
