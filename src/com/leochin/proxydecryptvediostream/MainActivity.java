package com.leochin.proxydecryptvediostream;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends Activity {
	
	private MediaPlayer mPlayer;
	private LocalProxy proxy;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);		
		
		proxy = new LocalProxy();
		proxy.init();
		proxy.start();

		initMediaPlayer();
		
	}
	
	private void initMediaPlayer(){
		
		//Uri uri = Uri.parse("http://172.20.223.172/letitgo.mp3");
		String str = proxy.getUrl("Music/letitgo.mp3");
		Uri uri = Uri.parse(str);
		mPlayer = MediaPlayer.create(this, uri);
	}

	/**
	 * 播放音乐
	 * 
	 * @param view
	 */
	public void play(View view){
		
		mPlayer.start();
	}
	
	public void stop(View view){
		
		mPlayer.stop();
	}

}
