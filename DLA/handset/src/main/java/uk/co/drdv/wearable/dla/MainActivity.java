package uk.co.drdv.wearable.dla;

import android.app.Activity;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.widget.LinearLayout;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View view = findViewById(R.id.title);
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        LinearLayout.LayoutParams llParams =
                new LinearLayout.LayoutParams(size.x, size.x * 250 / 512);
        view.setLayoutParams(llParams);
        view.requestLayout();
    }
}
