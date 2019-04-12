// Sekian#6855

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.math.Vector2;
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
    CountDownLatch latch;
    int nThreads = 20; //Average number of animations per skeleton
    public void create () {
        long start = System.currentTimeMillis();
        latch = new CountDownLatch(Integer.MAX_VALUE);
        batch = new PolygonSpriteBatch();
        renderer = new SkeletonRenderer();
        renderer.setPremultipliedAlpha(true);
        FileHandle dirHandle = Gdx.files.absolute("./");        
        FileHandle[] a = dirHandle.list(".skel");
        FileHandle[] b = dirHandle.list(".skel.txt");
        List<FileHandle> directory = new ArrayList<FileHandle>(a.length + b.length);
        directory.addAll(Arrays.asList(a));
        directory.addAll(Arrays.asList(b));        
        File file = new File("_out"); file.mkdirs();
        nThreads = Runtime.getRuntime().availableProcessors();
        pool = new ThreadPoolExecutor(nThreads, Integer.MAX_VALUE, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        for (FileHandle skeletonFile: directory) {
            long start2 = System.currentTimeMillis();
            SkeletonData skeletonData = loadSkeleton(skeletonFile);
            String skeletonName = skeletonFile.nameWithoutExtension();
            if (skeletonName.endsWith(".skel")) skeletonName = skeletonName.substring(0, skeletonName.length() - 5);
            file = new File("_out/" + skeletonName); file.mkdirs();
            System.out.print(skeletonName+" ");
            for (Animation animation : skeletonData.getAnimations()) {
                Vector2 min = new Vector2(0,0);
                Vector2 max = new Vector2(0,0);
                Vector2 pos = new Vector2(0,0);
                getAnimationBounds(min, max, skeletonData, animation);
                pos.x = Math.abs(min.x); 
                pos.y = Math.abs(min.y); 
                max.x += Math.abs(min.x);
                max.y += Math.abs(min.y);
                min.x = 0;
                min.y = 0;
                try {
                    int size = pool.getQueue().size();
                    if (size > nThreads) {
                        latch = new CountDownLatch(size - nThreads);
                        latch.await(8, TimeUnit.SECONDS);
                    }
                    saveAnimation(min, max, pos, skeletonData, animation);
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
    public void getAnimationBounds(Vector2 min, Vector2 max, SkeletonData skeletonData, Animation animation) {
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
    public ByteBuffer getFrameBufferPixels(int minX, int minY, int maxX, int maxY) {
		Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);
		final ByteBuffer pixels = ByteBuffer.allocateDirect(maxX * maxY * 4);
		pixels.order(ByteOrder.nativeOrder());
		Gdx.gl.glReadPixels(0, 0, maxX, maxY, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels);
		return pixels;
    }
    public void saveAnimation (Vector2 min, Vector2 max, Vector2 pos, SkeletonData skeletonData, Animation animation) throws Exception {
        AnimationState state = new AnimationState(new AnimationStateData(skeletonData));
        Skeleton skeleton = new Skeleton(skeletonData);
        Skeleton skeletonAlpha = new Skeleton(skeletonData);
        skeleton.setToSetupPose();
        skeleton.updateWorldTransform();
        skeletonAlpha.setToSetupPose();
        skeletonAlpha.updateWorldTransform();
        state.getData().setDefaultMix(0.0f);
        state.setAnimation(0, animation, false);
		int minX = (int)Math.floor(min.x);
		int minY = (int)Math.floor(min.y);
		int maxX = (int)Math.ceil(max.x)-minX;
		int maxY = (int)Math.ceil(max.y)-minY;
		int size = (int)(animation.getDuration()/delta) + 2;
		//List<ByteBuffer> frames = new ArrayList<ByteBuffer>(size);
		List<byte[]> frames = new ArrayList<byte[]>(size);
        while (!state.getCurrent(0).isComplete()) {
        	Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            state.update(delta);
            state.apply(skeleton);
            skeleton.setPosition(pos.x, pos.y);
            skeleton.updateWorldTransform();
            if (Gdx.graphics.getWidth() < maxX || Gdx.graphics.getHeight() < maxY)
            	Gdx.graphics.setWindowedMode(maxX, maxY);
            batch.begin();
			renderer.setPremultipliedAlpha(false);
			renderer.draw(batch, skeleton);
			batch.end();
			byte[] pixels = ScreenUtils.getFrameBufferPixels(minX, minY, maxX, maxY, false);
			batch.begin();
			renderer.setPremultipliedAlpha(true);
			renderer.draw(batch, skeleton);
			batch.end();	
			byte[] pixelsAlpha = ScreenUtils.getFrameBufferPixels(minX, minY, maxX, maxY, false);			
			for (int i = 4; i < pixels.length; i += 4) {
			    pixels[i - 1] = pixelsAlpha[i - 1];
			}
			//pixelsAlpha.clear();
            frames.add(pixels);
        }
        pool.execute(new Runnable() {
        @Override
	        public void run() {
	        	try {
					saveWork(frames, animation.getName(), skeletonData.getName(), maxX, maxY);
				} catch (IOException e) {
					e.printStackTrace();
				}
	        }
    	});      
    }
    public void saveWork(List<byte[]> frames, String animationName, String skeletonName, int maxX, int maxY) throws IOException {   
        if (skeletonName.endsWith(".skel")) 
            skeletonName = skeletonName.substring(0, skeletonName.length() - 5);        
        FileOutputStream fout = new FileOutputStream("_out/" + skeletonName +"/"+ animationName + ".zip");
        BufferedOutputStream bout = new BufferedOutputStream(fout);
        ZipOutputStream zout = new ZipOutputStream(bout);
        zout.setLevel(Deflater.NO_COMPRESSION);
        for (int ii = 0; ii < frames.size(); ++ii) {
            byte[] pixels = frames.get(ii);
            BufferedImage image = new BufferedImage(maxX, maxY, BufferedImage.TYPE_INT_ARGB);    
            for (int x = 0; x < maxX; x++) {
                for (int y = 0; y < maxY; y++) {
                    int i = (x + (maxX * y)) * 4;
                    int r = pixels[i + 0] & 0xFF;
                    int g = pixels[i + 1] & 0xFF;
                    int b = pixels[i + 2] & 0xFF;
                    int a = pixels[i + 3] & 0xFF;
                    image.setRGB(x, maxY - y - 1, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            pixels = null;
            frames.set(ii, null);
            String filePath = String.format("%04d.gif", ii);
            zout.putNextEntry(new ZipEntry(filePath));
            ImageIO.write(image, "gif", zout);
            zout.closeEntry(); 
            image.flush();
            image = null;
        }
        //frames.clear();
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
	    config.width = (int)(960);
	    config.height = (int)(720);
	    new LwjglApplication(anim, config);
    }
}
