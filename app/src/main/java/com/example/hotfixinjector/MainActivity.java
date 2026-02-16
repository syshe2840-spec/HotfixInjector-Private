package com.example.hotfixinjector;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends Activity {

	private FireParticleView fireView;  
	private Handler handler = new Handler();  

	@Override  
	protected void onCreate(Bundle savedInstanceState) {  
		super.onCreate(savedInstanceState);  
		createUI();  
	}  

	@Override  
	protected void onDestroy() {  
		super.onDestroy();  
		if (fireView != null) {  
			fireView.stopAnimation();  
		}  
		handler.removeCallbacksAndMessages(null);  
	}  

	private void createUI() {  
		FrameLayout root = new FrameLayout(this);  

		LinearLayout main = new LinearLayout(this);  
		main.setOrientation(LinearLayout.VERTICAL);  

		GradientDrawable bg = new GradientDrawable(  
			GradientDrawable.Orientation.TOP_BOTTOM,  
			new int[]{  
				Color.parseColor("#0a0614"),  
				Color.parseColor("#120820"),  
				Color.parseColor("#1a0c28"),  
				Color.parseColor("#0a0614")  
			}  
		);  
		main.setBackground(bg);  

		fireView = new FireParticleView(this);  
		FrameLayout.LayoutParams fireParams = new FrameLayout.LayoutParams(  
			ViewGroup.LayoutParams.MATCH_PARENT,  
			ViewGroup.LayoutParams.MATCH_PARENT  
		);  
		fireView.setLayoutParams(fireParams);  

		ScrollView scrollView = new ScrollView(this);  
		scrollView.setFillViewport(true);  
		scrollView.setVerticalScrollBarEnabled(false);  

		LinearLayout content = new LinearLayout(this);  
		content.setOrientation(LinearLayout.VERTICAL);  
		content.setGravity(Gravity.CENTER);  
		content.setPadding(20, 40, 20, 40);  

		content.addView(createHeader());  
		content.addView(createStatusCard());  
		content.addView(createCard(  
							"Step 1: Enable Module",  
							"• Open LSPosed Manager\n" +  
							"• Go to Modules tab\n" +  
							"• Enable HotFix Injector\n" +  
							"• Reboot device",  
							"#ff6b00"  
						));  
		content.addView(createCard(  
							"Step 2: Configure Scope",  
							"• Tap on HotFix Injector module\n" +  
							"• Select Scope section\n" +  
							"• Add your target applications\n" +  
							"• Save changes",  
							"#00aa00"  
						));  
		content.addView(createCard(  
							"Step 3: Create Hotfix Folder",  
							"Path: /data/data/YOUR_APP/hotfix\n\n" +  
							"• Use root file manager\n" +  
							"• Create 'hotfix' folder\n" +  
							"• Set permissions: 755",  
							"#0088ff"  
						));  
		content.addView(createCard(  
							"Step 4: Add DEX Files",  
							"• Compile your code to .dex\n" +  
							"• Copy DEX files to hotfix folder\n" +  
							"• Make sure permissions are correct\n" +  
							"• Example: patch.dex, hook.dex",  
							"#8800ff"  
						));  
		content.addView(createCard(
							"Step 5: Launch App",
							"• Open your target application\n" +
							"• DEX files inject automatically!\n" +
							"• Check logcat for injection logs\n" +
							"• Tag: HotfixInjector",
							"#ff0088"
						));

		// License Activation Button
		content.addView(createLicenseButton());

		scrollView.addView(content);  
		main.addView(scrollView);  

		root.addView(fireView);  
		root.addView(main);  

		setContentView(root);  
	}  

	private LinearLayout createHeader() {  
		LinearLayout header = new LinearLayout(this);  
		header.setOrientation(LinearLayout.VERTICAL);  
		header.setGravity(Gravity.CENTER);  

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(  
			ViewGroup.LayoutParams.MATCH_PARENT,  
			ViewGroup.LayoutParams.WRAP_CONTENT  
		);  
		params.setMargins(0, 0, 0, 30);  
		header.setLayoutParams(params);  

		FrameLayout iconContainer = new FrameLayout(this);  
		LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(180, 180);  
		iconParams.setMargins(0, 0, 0, 20);  
		iconContainer.setLayoutParams(iconParams);  

		for (int i = 0; i < 3; i++) {  
			TextView ring = new TextView(this);  
			ring.setText("◯");  
			ring.setTextSize(90 + (i * 15));  
			ring.setTextColor(Color.parseColor("#ff4400"));  
			ring.setShadowLayer(50, 0, 0, Color.parseColor("#ff2200"));  
			ring.setAlpha(0.15f - (i * 0.03f));  
			ring.setGravity(Gravity.CENTER);  

			FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(  
				ViewGroup.LayoutParams.MATCH_PARENT,  
				ViewGroup.LayoutParams.MATCH_PARENT  
			);  
			ring.setLayoutParams(ringParams);  

			RotateAnimation rotate = new RotateAnimation(  
				0, 360,  
				Animation.RELATIVE_TO_SELF, 0.5f,  
				Animation.RELATIVE_TO_SELF, 0.5f  
			);  
			rotate.setDuration(7000 + (i * 2000));  
			rotate.setRepeatCount(Animation.INFINITE);  
			rotate.setInterpolator(new AccelerateDecelerateInterpolator());  
			ring.startAnimation(rotate);  

			iconContainer.addView(ring);  
		}  

		TextView fire = new TextView(this);  
		fire.setText("🔥");  
		fire.setTextSize(45);  
		fire.setGravity(Gravity.CENTER);  
		fire.setShadowLayer(60, 0, 0, Color.parseColor("#ff4500"));  

		FrameLayout.LayoutParams fireParams = new FrameLayout.LayoutParams(  
			ViewGroup.LayoutParams.MATCH_PARENT,  
			ViewGroup.LayoutParams.MATCH_PARENT  
		);  
		fire.setLayoutParams(fireParams);  

		AnimationSet fireAnim = new AnimationSet(true);  

		ScaleAnimation pulse = new ScaleAnimation(  
			1.0f, 1.3f, 1.0f, 1.3f,  
			Animation.RELATIVE_TO_SELF, 0.5f,  
			Animation.RELATIVE_TO_SELF, 0.5f  
		);  
		pulse.setDuration(1400);  
		pulse.setRepeatCount(Animation.INFINITE);  
		pulse.setRepeatMode(Animation.REVERSE);  

		RotateAnimation wobble = new RotateAnimation(  
			-10, 10,  
			Animation.RELATIVE_TO_SELF, 0.5f,  
			Animation.RELATIVE_TO_SELF, 0.5f  
		);  
		wobble.setDuration(1800);  
		wobble.setRepeatCount(Animation.INFINITE);  
		wobble.setRepeatMode(Animation.REVERSE);  

		fireAnim.addAnimation(pulse);  
		fireAnim.addAnimation(wobble);  
		fire.startAnimation(fireAnim);  

		iconContainer.addView(fire);  

		TextView title = new TextView(this);  
		title.setText("HotFix Injector");  
		title.setTextSize(44);  
		title.setTextColor(Color.parseColor("#ff6600"));  
		title.setTypeface(null, Typeface.BOLD);  
		title.setGravity(Gravity.CENTER);  
		title.setShadowLayer(40, 0, 0, Color.parseColor("#ff3300"));  
		title.setLetterSpacing(0.1f);  

		LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(  
			ViewGroup.LayoutParams.WRAP_CONTENT,  
			ViewGroup.LayoutParams.WRAP_CONTENT  
		);  
		titleParams.setMargins(0, 0, 0, 8);  
		title.setLayoutParams(titleParams);  

		TextView subtitle = new TextView(this);  
		subtitle.setText("Setup Instructions");  
		subtitle.setTextSize(15);  
		subtitle.setTextColor(Color.parseColor("#aaaaaa"));  
		subtitle.setGravity(Gravity.CENTER);  
		subtitle.setLetterSpacing(0.2f);  

		header.addView(iconContainer);  
		header.addView(title);  
		header.addView(subtitle);  

		return header;  
	}  

	private LinearLayout createStatusCard() {  
		LinearLayout card = new LinearLayout(this);  
		card.setOrientation(LinearLayout.HORIZONTAL);  
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(  
			ViewGroup.LayoutParams.MATCH_PARENT,  
			ViewGroup.LayoutParams.WRAP_CONTENT  
		);  
		params.setMargins(0, 0, 0, 20);  
		card.setLayoutParams(params);  
		card.setPadding(24, 20, 24, 20);  
		card.setGravity(Gravity.CENTER_VERTICAL);  

		boolean active = isModuleActive();  

		GradientDrawable bg = new GradientDrawable();  
		bg.setCornerRadius(24);  
		if (active) {  
			bg.setColors(new int[]{  
							 Color.parseColor("#0d3d1e"),  
							 Color.parseColor("#1a5a2d")  
						 });  
			bg.setStroke(3, Color.parseColor("#00ff00"));  
		} else {  
			bg.setColors(new int[]{  
							 Color.parseColor("#3d0d0d"),  
							 Color.parseColor("#5a1a1a")  
						 });  
			bg.setStroke(3, Color.parseColor("#ff3300"));  
		}  
		bg.setGradientType(GradientDrawable.LINEAR_GRADIENT);  
		card.setBackground(bg);  

		TextView icon = new TextView(this);  
		icon.setText(active ? "✓" : "⚠");  
		icon.setTextSize(38);  
		icon.setTextColor(active ? Color.parseColor("#00ff00") : Color.parseColor("#ff3300"));  
		icon.setTypeface(null, Typeface.BOLD);  
		icon.setShadowLayer(20, 0, 0, active ? Color.parseColor("#00ff00") : Color.parseColor("#ff3300"));  
		icon.setPadding(0, 0, 18, 0);  

		ScaleAnimation iconPulse = new ScaleAnimation(  
			1.0f, 1.15f, 1.0f, 1.15f,  
			Animation.RELATIVE_TO_SELF, 0.5f,  
			Animation.RELATIVE_TO_SELF, 0.5f  
		);  
		iconPulse.setDuration(1000);  
		iconPulse.setRepeatCount(Animation.INFINITE);  
		iconPulse.setRepeatMode(Animation.REVERSE);  
		icon.startAnimation(iconPulse);  

		LinearLayout textBox = new LinearLayout(this);  
		textBox.setOrientation(LinearLayout.VERTICAL);  

		TextView statusTitle = new TextView(this);  
		statusTitle.setText(active ? "MODULE ACTIVE" : "MODULE INACTIVE");  
		statusTitle.setTextSize(17);  
		statusTitle.setTextColor(Color.WHITE);  
		statusTitle.setTypeface(null, Typeface.BOLD);  

		TextView statusDesc = new TextView(this);  
		statusDesc.setText(active ? "Ready to inject" : "Follow steps below");  
		statusDesc.setTextSize(13);  
		statusDesc.setTextColor(Color.parseColor("#cccccc"));  
		statusDesc.setPadding(0, 5, 0, 0);  

		textBox.addView(statusTitle);  
		textBox.addView(statusDesc);  
		card.addView(icon);  
		card.addView(textBox);  

		return card;  
	}  

	private LinearLayout createCard(String titleText, String content, String color) {  
		LinearLayout card = new LinearLayout(this);  
		card.setOrientation(LinearLayout.VERTICAL);  
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(  
			ViewGroup.LayoutParams.MATCH_PARENT,  
			ViewGroup.LayoutParams.WRAP_CONTENT  
		);  
		params.setMargins(0, 0, 0, 14);  
		card.setLayoutParams(params);  
		card.setPadding(22, 20, 22, 20);  

		GradientDrawable bg = new GradientDrawable();  
		bg.setCornerRadius(20);  
		bg.setColor(Color.parseColor("#1a0f28"));  
		bg.setStroke(3, Color.parseColor(color));  
		card.setBackground(bg);  

		TextView title = new TextView(this);  
		title.setText(titleText);  
		title.setTextSize(17);  
		title.setTextColor(Color.parseColor(color));  
		title.setTypeface(null, Typeface.BOLD);  
		title.setShadowLayer(10, 0, 0, Color.parseColor(color));  
		title.setPadding(0, 0, 0, 12);  

		TextView desc = new TextView(this);  
		desc.setText(content);  
		desc.setTextSize(14);  
		desc.setTextColor(Color.parseColor("#eeeeee"));  
		desc.setLineSpacing(6, 1);  

		card.addView(title);  
		card.addView(desc);  

		AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);  
		fadeIn.setDuration(500);  
		card.startAnimation(fadeIn);  

		return card;  
	}  

	private TextView createFireButton(String text) {  
		TextView btn = new TextView(this);  
		btn.setText(text);  
		btn.setTextSize(18);  
		btn.setTextColor(Color.WHITE);  
		btn.setTypeface(null, Typeface.BOLD);  
		btn.setGravity(Gravity.CENTER);  
		btn.setPadding(40, 24, 40, 24);  

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(  
			ViewGroup.LayoutParams.MATCH_PARENT,  
			ViewGroup.LayoutParams.WRAP_CONTENT  
		);  
		params.setMargins(0, 10, 0, 0);  
		btn.setLayoutParams(params);  

		GradientDrawable bg = new GradientDrawable();  
		bg.setCornerRadius(20);  
		bg.setColors(new int[]{  
						 Color.parseColor("#ff4400"),  
						 Color.parseColor("#ff6600")  
					 });  
		bg.setGradientType(GradientDrawable.LINEAR_GRADIENT);  
		bg.setStroke(3, Color.parseColor("#ff8800"));  
		btn.setBackground(bg);  
		btn.setShadowLayer(30, 0, 0, Color.parseColor("#ff4400"));  

		btn.setOnClickListener(new View.OnClickListener() {  
				@Override  
				public void onClick(View v) {  
					burstFire(v);  
				}  
			});  

		return btn;  
	}  

	private void burstFire(View v) {  
		ScaleAnimation scale = new ScaleAnimation(  
			1.0f, 0.9f, 1.0f, 0.9f,  
			Animation.RELATIVE_TO_SELF, 0.5f,  
			Animation.RELATIVE_TO_SELF, 0.5f  
		);  
		scale.setDuration(100);  
		scale.setRepeatCount(1);  
		scale.setRepeatMode(Animation.REVERSE);  
		v.startAnimation(scale);  

		if (fireView != null) {  
			fireView.burst();  
		}  
	}  

	private boolean isModuleActive() {
		return false;
	}

	private TextView createLicenseButton() {
		TextView btn = new TextView(this);
		btn.setText("🔐 LICENSE ACTIVATION");
		btn.setTextSize(18);
		btn.setTextColor(Color.WHITE);
		btn.setTypeface(null, Typeface.BOLD);
		btn.setGravity(Gravity.CENTER);
		btn.setPadding(40, 28, 40, 28);

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
		);
		params.setMargins(0, 20, 0, 0);
		btn.setLayoutParams(params);

		GradientDrawable bg = new GradientDrawable();
		bg.setCornerRadius(20);
		bg.setColors(new int[]{
			Color.parseColor("#8800ff"),
			Color.parseColor("#aa00ff")
		});
		bg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
		bg.setStroke(3, Color.parseColor("#cc00ff"));
		btn.setBackground(bg);
		btn.setShadowLayer(30, 0, 0, Color.parseColor("#8800ff"));

		btn.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainActivity.this, LicenseActivationActivity.class);
				startActivity(intent);
			}
		});

		return btn;
	}

	private class FireParticleView extends View {  
		private List<FireParticle> particles;  
		private Paint paint;  
		private Handler handler;  
		private Random random;  
		private boolean isRunning;  
		private int screenWidth;  
		private int screenHeight;  

		public FireParticleView(Context context) {  
			super(context);  
			particles = new ArrayList<FireParticle>();  
			paint = new Paint(Paint.ANTI_ALIAS_FLAG);  
			handler = new Handler();  
			random = new Random();  
			isRunning = true;  
		}  

		@Override  
		protected void onSizeChanged(int w, int h, int oldw, int oldh) {  
			super.onSizeChanged(w, h, oldw, oldh);  
			screenWidth = w;  
			screenHeight = h;  

			if (screenWidth > 0 && screenHeight > 0 && !isRunning) {  
				isRunning = true;  
				startAnimation();  
			}  
		}  

		private void startAnimation() {  
			if (screenWidth <= 0 || screenHeight <= 0) return;  

			handler.post(new Runnable() {  
					@Override  
					public void run() {  
						if (!isRunning || screenWidth <= 0 || screenHeight <= 0) return;  

						if (particles.size() < 20 && random.nextFloat() < 0.4f) {  
							particles.add(new FireParticle(random.nextInt(screenWidth), screenHeight));  
						}  

						for (int i = particles.size() - 1; i >= 0; i--) {  
							FireParticle p = particles.get(i);  
							p.update();  
							if (p.isDead()) {  
								particles.remove(i);  
							}  
						}  

						invalidate();  
						handler.postDelayed(this, 16);  
					}  
				});  
		}  

		public void burst() {  
			if (screenWidth <= 0 || screenHeight <= 0) return;  

			int centerX = screenWidth / 2;  
			int centerY = screenHeight / 2;  

			for (int i = 0; i < 30; i++) {  
				particles.add(new FireParticle(centerX, centerY, true));  
			}  
		}  

		public void stopAnimation() {  
			isRunning = false;  
			handler.removeCallbacksAndMessages(null);  
		}  

		@Override  
		protected void onDraw(Canvas canvas) {  
			super.onDraw(canvas);  

			for (FireParticle p : particles) {  
				p.draw(canvas, paint);  
			}  
		}  

		private class FireParticle {  
			float x, y;  
			float vx, vy;  
			float size;  
			float life;  
			float maxLife;  
			int colorType;  

			FireParticle(int startX, int startY) {  
				this(startX, startY, false);  
			}  

			FireParticle(int startX, int startY, boolean burst) {  
				x = startX;  
				y = startY;  

				if (burst) {  
					float angle = random.nextFloat() * 360;  
					float speed = 3 + random.nextFloat() * 8;  
					vx = (float)(Math.cos(Math.toRadians(angle)) * speed);  
					vy = (float)(Math.sin(Math.toRadians(angle)) * speed);  
					size = 15 + random.nextFloat() * 30;  
				} else {  
					vx = (random.nextFloat() - 0.5f) * 3;  
					vy = -4 - random.nextFloat() * 4;  
					size = 12 + random.nextFloat() * 25;  
				}  

				maxLife = 1.0f;  
				life = maxLife;  
				colorType = random.nextInt(5);  
			}  

			void update() {  
				x += vx;  
				y += vy;  
				vy -= 0.08f;  
				vx *= 0.99f;  
				life -= 0.012f;  
				size *= 0.97f;  
			}  

			boolean isDead() {  
				return life <= 0 || y < -150;  
			}  

			void draw(Canvas canvas, Paint paint) {  
				float alpha = Math.max(0, Math.min(1, life / maxLife));  

				int centerColor, edgeColor;  

				switch(colorType) {  
					case 0:  
						centerColor = Color.argb((int)(255 * alpha), 255, 255, 100);  
						edgeColor = Color.argb(0, 255, 200, 0);  
						break;  
					case 1:  
						centerColor = Color.argb((int)(255 * alpha), 255, 200, 0);  
						edgeColor = Color.argb(0, 255, 100, 0);  
						break;  
					case 2:  
						centerColor = Color.argb((int)(255 * alpha), 255, 150, 0);  
						edgeColor = Color.argb(0, 255, 50, 0);  
						break;  
					case 3:  
						centerColor = Color.argb((int)(255 * alpha), 255, 100, 0);  
						edgeColor = Color.argb(0, 200, 0, 0);  
						break;  
					default:  
						centerColor = Color.argb((int)(255 * alpha), 255, 50, 0);  
						edgeColor = Color.argb(0, 150, 0, 0);  
						break;  
				}  

				RadialGradient gradient = new RadialGradient(  
					x, y, size,  
					centerColor,  
					edgeColor,  
					Shader.TileMode.CLAMP  
				);  

				paint.setShader(gradient);  
				canvas.drawCircle(x, y, size, paint);  
				paint.setShader(null);  
			}  
		}  
	}

}
