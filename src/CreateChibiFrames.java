// Sekian#6855

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.PixmapIO.PNG;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ScreenUtils;
import com.esotericsoftware.spine.Animation;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonRenderer;

public class CreateChibiFrames extends ApplicationAdapter {
    SkeletonRenderer renderer;
    AnimationState state;
    float delta = 1/30.0f;
    PolygonSpriteBatch batch;
    ThreadPoolExecutor pool;
	
    public void create () {
        long start = System.currentTimeMillis();
        int nThreads = Runtime.getRuntime().availableProcessors();
        batch = new PolygonSpriteBatch();
        renderer = new SkeletonRenderer();
        renderer.setPremultipliedAlpha(true);
        FileHandle dirHandle = Gdx.files.absolute("./");        
        FileHandle[] a = dirHandle.list(".skel");
        FileHandle[] b = dirHandle.list(".skel.txt");
        List<FileHandle> directory = new ArrayList<FileHandle>(a.length + b.length);
        pool = new ThreadPoolExecutor(nThreads, 100, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        directory.addAll(Arrays.asList(a));
        directory.addAll(Arrays.asList(b));        
        File file = new File("_out");
        file.mkdirs();
        for  (FileHandle skeletonFile: directory) {
            long start2 = System.currentTimeMillis();
            SkeletonData skeletonData = loadSkeleton(skeletonFile);
            String skeletonName = skeletonFile.nameWithoutExtension();
            if (skeletonName.endsWith(".skel")) skeletonName = skeletonName.substring(0, skeletonName.length() - 5);
            System.out.print(skeletonName+" ");
            for (Animation animation : skeletonData.getAnimations()) {
                Vector2 min = new Vector2(0,0);
                Vector2 max = new Vector2(0,0);
                Vector2 pos = new Vector2(0,0);
                getAnimationBounds(min, max, skeletonData, animation.getName());
                pos.x = Math.abs(min.x); 
                pos.y = Math.abs(min.y); 
                max.x += Math.abs(min.x);
                max.y += Math.abs(min.y);
                min.x = 0;
                min.y = 0;
                try {
                    saveAnimation(min, max, pos, skeletonData, animation.getName());
                }   catch (Exception e) {e.printStackTrace();}
            }
            long end2 = System.currentTimeMillis();
            System.out.print((end2 - start2)/1000.0 + "s\n");
        }
        pool.shutdown();
        try {
            while(!pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MINUTES));
        } catch (InterruptedException e) {e.printStackTrace();}
        long end = System.currentTimeMillis();
        System.out.println((end - start)/1000.0 + "s");
        
        Gdx.app.exit();
    }
    public SkeletonData loadSkeleton (FileHandle skeletonFile) {
        if (skeletonFile == null) return null;
        String atlasFileName = skeletonFile.nameWithoutExtension();
        if (atlasFileName.endsWith(".skel"))
            atlasFileName = atlasFileName.substring(0, atlasFileName.length() - 5);
        //System.out.println(atlasFileName);
        FileHandle atlasFile = skeletonFile.sibling(atlasFileName + ".atlas");
        if (!atlasFile.exists()) 
            atlasFile = skeletonFile.sibling(atlasFileName + ".atlas.txt");
        
        TextureAtlasData data = new TextureAtlasData(atlasFile, atlasFile.parent(), false);
        TextureAtlas atlas = new TextureAtlas(data);        
        SkeletonBinary binary = new SkeletonBinary(atlas);
        SkeletonData skeletonData = binary.readSkeletonData(skeletonFile);
        return skeletonData;
    }	
	//max and min world coordinates for all the animation
    public void getAnimationBounds(Vector2 min, Vector2 max, SkeletonData skeletonData, String animation) {
        float minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        Skeleton skeleton = new Skeleton(skeletonData);
        skeleton.setToSetupPose();
        skeleton = new Skeleton(skeleton);
        skeleton.updateWorldTransform();
        AnimationState state = new AnimationState(new AnimationStateData(skeleton.getData()));
        state.getData().setDefaultMix(0.0f);
        state.setAnimation(0, animation, false); 
        while (!state.getCurrent(0).isComplete()) {
            skeleton.update(delta);
            state.update(delta);
            state.apply(skeleton);
            skeleton.setPosition(0, 0);
            skeleton.updateWorldTransform();
            skeleton.getBounds(min, max, new FloatArray());    
            maxX = Math.max(maxX, max.x+min.x); //Cancel the substracted min returned from getBounds
            maxY = Math.max(maxY, max.y+min.y); //because we want the absolute max value (not the distance)
            minX = Math.min(minX, min.x);
            minY = Math.min(minY, min.y);
        }
        min.set(minX, minY);
        max.set(maxX, maxY);
        //System.out.println(minX +" "+ minY);
        //System.out.println(maxX +" "+ maxY);
    }
    public void saveAnimation (Vector2 min, Vector2 max, Vector2 pos, SkeletonData skeletonData, String animation) throws Exception {
        AnimationState state = new AnimationState(new AnimationStateData(skeletonData));
        Skeleton skeleton = new Skeleton(skeletonData);
        skeleton.setToSetupPose();
        skeleton = new Skeleton(skeleton);
        skeleton.updateWorldTransform();
        state.getData().setDefaultMix(0.0f);
        state.setAnimation(0, animation, false);
		int minX = (int)Math.floor(min.x);
		int minY = (int)Math.floor(min.y);
		int maxX = (int)Math.ceil(max.x)-minX;
		int maxY = (int)Math.ceil(max.y)-minY;	
        List<Pixmap> frames = new ArrayList<Pixmap>(); 
        while (!state.getCurrent(0).isComplete()) {
        	Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.setPremultipliedAlpha(false);
            state.update(delta);
            state.apply(skeleton);            
            skeleton.setPosition(pos.x, pos.y);
            skeleton.updateWorldTransform();                
			batch.begin();
			renderer.draw(batch, skeleton);
			batch.end();	
			byte[] pixels = ScreenUtils.getFrameBufferPixels(minX, minY, maxX, maxY, true);
			renderer.setPremultipliedAlpha(true);
			batch.begin();
			renderer.draw(batch, skeleton);
			batch.end();
			byte[] pixelsAlpha = ScreenUtils.getFrameBufferPixels(minX, minY, maxX, maxY, true);				
			for(int i = 4; i < pixels.length; i += 4) {
			    pixels[i - 1] = pixelsAlpha[i - 1];
			}
            Pixmap pixmap = new Pixmap(maxX, maxY, Pixmap.Format.RGBA8888);
            BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
            frames.add(pixmap);
        }
        pool.execute(new Runnable() {
        @Override
	        public void run() {
	        	try {
					saveWork(frames, state.getCurrent(0).toString(), skeletonData.getName());
				} catch (IOException e) {
					e.printStackTrace();
				}
	        }
    	});       
    }
	public void doWork(Pixmap pixmap, String fileName) {
		PixmapIO.writePNG(Gdx.files.absolute(fileName), pixmap);
		pixmap.dispose();
	}
	public void saveWork(List<Pixmap> frames, String animationName, String skeletonName) throws IOException {	
        if (skeletonName.endsWith(".skel")) 
        	skeletonName = skeletonName.substring(0, skeletonName.length() - 5);
	    File file = new File("_out/" + skeletonName); file.mkdirs();
        FileOutputStream fout = new FileOutputStream("_out/" + skeletonName +"/"+ animationName + ".zip");
        BufferedOutputStream bout = new BufferedOutputStream(fout);
        ZipOutputStream zout = new ZipOutputStream(bout);  
        PNG encoder = new PixmapIO.PNG();
        encoder.setFlipY(false);
        for (int i = 0; i < frames.size(); ++i) {
        	String filePath = String.format("%04d.png", i);
        	zout.putNextEntry(new ZipEntry(filePath));
    		encoder.write(zout, frames.get(i));
    		zout.closeEntry();
    		frames.get(i).dispose();
        }
        frames.clear();
        frames = null;
        zout.close();
        bout.close();
        fout.close();
	}
    public static void main(String [] args) {
	    CreateChibiFrames anim = new CreateChibiFrames();
	    if (args.length >= 1) anim.delta = 1/Float.parseFloat(args[0]);
	    System.out.println(Math.round(1/anim.delta));
	    LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
	    config.width = (int)(640);
	    config.height = (int)(360);
	    new LwjglApplication(anim, config);
    }
}
