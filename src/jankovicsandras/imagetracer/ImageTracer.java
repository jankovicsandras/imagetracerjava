/*

TODO: backporting to imagetracer.js
 - in pathscan(): holepath = true; instead of paths.type = "hole"
 - remove unnecessary path.type from pathscan in internodes() .... 
 - getsvgstring() Z-indexing: a zindex[label] should contain only 1 path, sublist is not required
 - in tracepath(): discard first seqence part, remember the start of the second sequence, wrap over the last sequence
 - remove unnecessary defaults in functions
 - fixing colorquantization() and layering() with -1 boundary 
 
 ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	ImageTracer.java (Desktop version with javax.imageio. See ImageTracerAndroid.java for the the Android version.) 
	Simple raster image tracer and vectorizer written in Java. This is a port of imagetracer.js. 
	by András Jankovics 2015
	andras@jankovics.net
	
	Tips:
	 - ltres,qtres : lower linear (ltres) and quadratic spline (qtres) error tresholds result 
	   more details at the cost of longer paths (more segments). Usually 0.5 or 1 is good for both. When tracing
	   round shapes, lower ltres, e.g. ltres=0.2 qtres=1 will result more curves, thus maybe better quality. Similarly,
	   ltres=1 qtres=0 is better for polygonal shapes. Values greater than 2 will usually result inaccurate paths.
	 - pathomit : the length of the shortest path is 4, four corners around one pixel. The default pathomit=8 filters out
	   isolated one and two pixels for noise reduction. This can be deactivated by setting pathomit=0.
	 - pal, numberofcolors : custom palette in the format pal=[colornum][4]; or automatic palette 
	   with the given length. Many colors will result many layers, so longer processing time and more paths, but better 
	   quality. When using few colors, more colorquantcycles can improve quality.
	 - mincolorratio : minimum ratio of pixels, below this the color will be randomized. mincolorratio=0.02 for a 10*10 image
	   means that if a color has less than 10*10 * 0.02 = 2 pixels, it will be randomized.
	 - colorquantcycles : color quantization will be repeated this many times. When using many colors, this can be a lower value.
	 - scale : optionally, the SVG output can be scaled, scale=2 means the SVG will have double height and width
	
	Process overview
	----------------
	
	// 1. Color quantization
	// 2. Layer separation and edge detection
	// 3. Batch pathscan
	// 4. Batch interpollation
	// 5. Batch tracing
	// 6. Creating SVG string

*/

/*

The Unlicense / PUBLIC DOMAIN

This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled
binary, for any purpose, commercial or non-commercial, and by any
means.

In jurisdictions that recognize copyright laws, the author or authors
of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit
of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of
relinquishment in perpetuity of all present and future rights to this
software under copyright law.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

For more information, please refer to http://unlicense.org/

*/
package jankovicsandras.imagetracer;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.imageio.ImageIO;

public class ImageTracer{

	public static String versionnumber = "1.0.2";
	
	public ImageTracer(){}
	
	public static void main(String[] args){
		try{
			
			if(args.length<1){
				System.out.println("ERROR: there's no input filename. Basic usage: \r\n\r\njava -jar ImageTracer.jar <filename>"+
					"\r\n\r\nor\r\n\r\njava -jar ImageTracer.jar help");
			} else if(arraycontains(args,"help")>-1){
				System.out.println("Example usage:\r\n\r\njava -jar ImageTracer.jar <filename> ltres 1 qtres 1 pathomit 8 numberofcolors 16 mincolorratio 0.02 colorquantcycles 3 scale 1 lcpr 0 qcpr 0\r\n\r\n"+
					"Only <filename> is mandatory, if some of the other optional parameters are missing, "+
					"they will be set to these defaults. See https://github.com/jankovicsandras/imagetracerjava for details. This is version "+versionnumber);
			} else {
			
				// Parameter parsing
				HashMap<String,Float> options = new HashMap<String,Float>();
				String[] parameternames = {"ltres","qtres","pathomit","numberofcolors","mincolorratio","colorquantcycles","scale","lcpr","qcpr"};
				int j = -1; float f = -1;
				for(int i=0; i<parameternames.length; i++){
					j = arraycontains(args,parameternames[i]);
					if(j>-1){ f = parsenext(args,j); if(f>-1){ options.put(parameternames[i], new Float(f)); } }
				}
				
				// Loading image, tracing, rendering SVG, saving SVG file 
				saveString(args[0]+".svg",imageToSVG(args[0],options));
			
			}
			
		}catch(Exception e){ e.printStackTrace(); }
	}// End of main()
	
	public static int arraycontains(String [] arr, String str){
		for(int j=0; j<arr.length; j++ ){ if(arr[j].toLowerCase().equals(str)){ return j; } } return -1;
	}
	
	public static float parsenext(String [] arr, int i){
		if(i<arr.length-1){ try{ return Float.parseFloat(arr[i+1]); }catch(Exception e){} } return -1;
	}
	
	public static void log(String msg){ System.out.println(msg); }
	
	// Container for the color-indexed image before and tracedata after vectorizing
	public static class IndexedImage{
		public int [][] array; // array[x][y] of palette colors
		public byte [][] palette;// array[palettelength][4] RGBA color palette
		public int background;// palette index of background color
		public ArrayList<ArrayList<ArrayList<Double[]>>> layers;// tracedata
		
		public IndexedImage(int [][] marray, byte [][] mpalette, int mbackground){
			array = marray; palette = mpalette; background = mbackground;
		}
	}
	
	// https://developer.mozilla.org/en-US/docs/Web/API/ImageData
	public static class ImageData{
		public int width, height;
		public byte[] data; // raw byte data: R G B A R G B A ...
		public ImageData(int mwidth, int mheight, byte[] mdata){
			width = mwidth; height = mheight; data = mdata;
		}
	}
	
	// Saving a String as a file
	public static void saveString(String filename, String str) throws Exception {
		File file = new File(filename);
		// if file doesnt exists, then create it
		if(!file.exists()){ file.createNewFile(); }
		FileWriter fw = new FileWriter(file.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(str);
		bw.close();
	}
	
	// Loading a file to ImageData, RGBA byte order
	public static ImageData loadImageData(String filename) throws Exception {
		BufferedImage image = ImageIO.read(new File(filename));
		return loadImageData(image);
	}
	public static ImageData loadImageData(BufferedImage image) throws Exception {
		int width = image.getWidth(); int height = image.getHeight();
		int[] rawdata = image.getRGB(0, 0, width, height, null, 0, width);
		byte[] data = new byte[rawdata.length*4];
		for(int i=0; i<rawdata.length; i++){
			data[i*4+3] = bytetrans((byte)(rawdata[i] >>> 24));
			data[i*4  ] = bytetrans((byte)(rawdata[i] >>> 16));
			data[i*4+1] = bytetrans((byte)(rawdata[i] >>> 8));
			data[i*4+2] = bytetrans((byte)(rawdata[i]));
		}
		return new ImageData(width,height,data);
	}
	
	// The bitshift method in loadImageData creates signed bytes where -1 -> 255 unsigned ; -128 -> 128 unsigned ;
	// 127 -> 127 unsigned ; 0 -> 0 unsigned ; These will be converted to -128 (representing 0 unsigned) ... 
	// 127 (representing 255 unsigned) and torgbstr will add +128 to create RGB values 0..255
	public static byte bytetrans(byte b){
		if(b<0){ return (byte)(b+128); }else{ return (byte)(b-128); }
	}
		
	////////////////////////////////////////////////////////////
	//
	//  User friendly functions
	//
	////////////////////////////////////////////////////////////
	
	// Loading an image from a file, tracing when loaded, then returning the SVG String
	public static String imageToSVG(String filename, HashMap<String,Float> options) throws Exception{
		options = checkoptions(options);
		ImageData imgd = loadImageData(filename);
		return imagedataToSVG(imgd,options);
	}// End of imageToSVG()
	public static String imageToSVG(BufferedImage image, HashMap<String,Float> options) throws Exception{
		options = checkoptions(options);
		ImageData imgd = loadImageData(image);
		return imagedataToSVG(imgd,options);
	}// End of imageToSVG()

	// Tracing ImageData, then returning the SVG String
	public static String imagedataToSVG (ImageData imgd, HashMap<String,Float> options){
		options = checkoptions(options);
		IndexedImage ii = imagedataToTracedata(imgd,options);
		return getsvgstring( 
				imgd.width*options.get("scale"), imgd.height*options.get("scale"), 
				ii,
				options.get("scale"), options.get("lcpr"), options.get("qcpr"));
	}// End of imagedataToSVG()
	
	// Loading an image from a file, tracing when loaded, then returning IndexedImage with tracedata in layers
	public IndexedImage imageToTracedata(String filename, HashMap<String,Float> options) throws Exception{
		options = checkoptions(options);
		ImageData imgd = loadImageData(filename);
		return imagedataToTracedata(imgd,options);
	}// End of imageToTracedata()
	public IndexedImage imageToTracedata(BufferedImage image, HashMap<String,Float> options) throws Exception{
		options = checkoptions(options);
		ImageData imgd = loadImageData(image);
		return imagedataToTracedata(imgd,options);
	}// End of imageToTracedata()
	
	// Tracing ImageData, then returning IndexedImage with tracedata in layers
	public static IndexedImage imagedataToTracedata (ImageData imgd, HashMap<String,Float> options){
		// 1. Color quantization
		IndexedImage ii = colorquantization(imgd, null, (int)(Math.floor(options.get("numberofcolors"))), options.get("mincolorratio"), (int)(Math.floor(options.get("colorquantcycles"))));
		// 2. Layer separation and edge detection
		int[][][] rawlayers = layering(ii);
		// 3. Batch pathscan
		ArrayList<ArrayList<ArrayList<Integer[]>>> bps = batchpathscan(rawlayers, (int)(Math.floor(options.get("pathomit"))));
		// 4. Batch interpollation
		ArrayList<ArrayList<ArrayList<Double[]>>> bis = batchinternodes(bps);
		// 5. Batch tracing
		ii.layers = batchtracelayers(bis,options.get("ltres"),options.get("qtres"));
		return ii;
	}// End of imagedataToTracedata()
	
	// creating options object, setting defaults for missing values
	public static HashMap<String,Float> checkoptions(HashMap<String,Float> options){
		if(options==null){ options = new HashMap<String,Float>();}
		// Tracing
		if(options.get("ltres")==null){ options.put("ltres",1f);}
		if(options.get("qtres")==null){ options.put("qtres",1f);}
		if(options.get("pathomit")==null){ options.put("pathomit",8f);}
		// Color quantization
		if(options.get("numberofcolors")==null){ options.put("numberofcolors",16f);}
		if(options.get("mincolorratio")==null){ options.put("mincolorratio",0.02f);}
		if(options.get("colorquantcycles")==null){ options.put("colorquantcycles",3f);}
		// SVG rendering
		if(options.get("scale")==null){ options.put("scale",1f);}
		if(options.get("lcpr")==null){ options.put("lcpr",0f);}
		if(options.get("qcpr")==null){ options.put("qcpr",0f);}
		return options;
	}// End of checkoptions()
	
	
	////////////////////////////////////////////////////////////
	//
	//  Vectorizing functions
	//
	////////////////////////////////////////////////////////////
	
	// 1. Color quantization repeated 'cycles' times, based on K-means clustering 
	// https://en.wikipedia.org/wiki/Color_quantization    https://en.wikipedia.org/wiki/K-means_clustering
	public static IndexedImage colorquantization (ImageData imgd, byte [][] palette, int numberofcolors, float minratio, int cycles){
		// Creating indexed color array arr which has a boundary filled with -1 in every direction
		int [][] arr = new int[imgd.height+2][imgd.width+2];
		for(int j=0; j<imgd.height+2; j++){ arr[j][0] = -1; arr[j][imgd.width+1 ] = -1; }
		for(int i=0; i<imgd.width+2 ; i++){ arr[0][i] = -1; arr[imgd.height+1][i] = -1; }
		
		int idx=0, cd,cdl,ci,c1,c2,c3;
		
		if(palette==null){ palette = generatepalette(numberofcolors); }

		long [][] paletteacc = new long[palette.length][4]; 
		
		// Repeat clustering step "cycles" times
		for(int cnt=0;cnt<cycles;cnt++){
			
			// Reseting palette accumulator for averaging
			for(int i=0;i<palette.length;i++){
				paletteacc[i][0]=0;
				paletteacc[i][1]=0;
				paletteacc[i][2]=0;
				paletteacc[i][3]=0;
			}
			
			// loop through all pixels
			for(int j=0;j<imgd.height;j++){
				for(int i=0;i<imgd.width;i++){
					
					idx = (j*imgd.width+i)*4;
					// find closest color from palette
					cdl = 256+256+256; ci=0;
					for(int k=0;k<palette.length;k++){	
						// In my experience, https://en.wikipedia.org/wiki/Rectilinear_distance works better than https://en.wikipedia.org/wiki/Euclidean_distance
						c1 = Math.abs(palette[k][0]-imgd.data[idx]);
						c2 = Math.abs(palette[k][1]-imgd.data[idx+1]);
						c3 = Math.abs(palette[k][2]-imgd.data[idx+2]);
						cd = c1+c2+c3;
						if(cd<cdl){
							cdl = cd; ci = k;
						}
					}// End of palette loop
					
					// add to palettacc
					paletteacc[ci][0] += imgd.data[idx];
					paletteacc[ci][1] += imgd.data[idx+1];
					paletteacc[ci][2] += imgd.data[idx+2];
					paletteacc[ci][3]++;
					
					arr[j+1][i+1] = ci; 
				}// End of i loop
			}// End of j loop
			
			// averaging paletteacc for palette
			float ratio;
			for(int k=0;k<palette.length;k++){
				// averaging
				if(paletteacc[k][3]>0){
					palette[k][0] = (byte) Math.floor(paletteacc[k][0]/paletteacc[k][3]);
					palette[k][1] = (byte) Math.floor(paletteacc[k][1]/paletteacc[k][3]);
					palette[k][2] = (byte) Math.floor(paletteacc[k][2]/paletteacc[k][3]);
				}
				ratio = paletteacc[k][3]/(imgd.width*imgd.height);
				
				// Randomizing a color, if there are too few pixels and there will be a new cycle
				if((ratio<minratio)&&(cnt<cycles-1)){
					palette[k][0] = (byte) Math.floor(Math.random()*255);
					palette[k][1] = (byte) Math.floor(Math.random()*255);
					palette[k][2] = (byte) Math.floor(Math.random()*255);
				}
				
			}// End of palette loop
			
		}// End of Repeat clustering step "cycles" times
		
		return new IndexedImage(arr, palette, arr[1][1]);//{"array":arr,"palette":palette,"background":arr[1][1]};
	}// End of colorquantization
	
	// Generating a palette with numberofcolors, array[numberofcolors][4] where [i][0] = R ; [i][1] = G ; [i][2] = B ; [i][3] = A 
	public static byte[][] generatepalette(int numberofcolors){
		byte [][] palette = new byte[numberofcolors][4];
		if(numberofcolors<8){ 
			
			// Grayscale
			byte graystep = (byte) Math.floor(255/(numberofcolors-1));
			for(byte ccnt=0;ccnt<numberofcolors;ccnt++){
				palette[ccnt][0] = (byte)(ccnt*graystep);
				palette[ccnt][1] = (byte)(ccnt*graystep);
				palette[ccnt][2] = (byte)(ccnt*graystep);
				palette[ccnt][3] = (byte)255;
			}
			
		}else{ 
			
			// RGB color cube
			int colorqnum = (int) Math.floor(Math.pow(numberofcolors, 1.0/3.0)); // Number of points on each edge on the RGB color cube
			int colorstep = (int) Math.floor(255/(colorqnum-1)); // distance between points
			int ccnt = 0;
			for(int rcnt=0;rcnt<colorqnum;rcnt++){
				for(int gcnt=0;gcnt<colorqnum;gcnt++){
					for(int bcnt=0;bcnt<colorqnum;bcnt++){
						palette[ccnt][0] = (byte)(rcnt*colorstep);
						palette[ccnt][1] = (byte)(gcnt*colorstep);
						palette[ccnt][2] = (byte)(bcnt*colorstep);
						palette[ccnt][3] = (byte)255;
						ccnt++;
					}// End of blue loop
				}// End of green loop
			}// End of red loop
			
			// Rest is random
			for(int rcnt=ccnt;rcnt<numberofcolors;rcnt++){
				palette[ccnt][0] = (byte)(Math.floor(Math.random()*255));
				palette[ccnt][1] = (byte)(Math.floor(Math.random()*255));
				palette[ccnt][2] = (byte)(Math.floor(Math.random()*255));
				palette[ccnt][3] = (byte)255;
			}

		}// End of numberofcolors check
		
		return palette;
	};// End of generatepalette()
	
	// 2. Layer separation and edge detection
	// Edge node types ( ▓:light or 1; ░:dark or 0 )
	// 12  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓
	// 48  ░░  ░░  ░░  ░░  ░▓  ░▓  ░▓  ░▓  ▓░  ▓░  ▓░  ▓░  ▓▓  ▓▓  ▓▓  ▓▓
	//     0   1   2   3   4   5   6   7   8   9   10  11  12  13  14  15
	//
	public static int[][][] layering (IndexedImage im){
		// Creating layers for each indexed color in arr
		int val=0, ah = im.array.length, aw = im.array[0].length, n1,n2,n3,n4,n5,n6,n7,n8;
		int[][][] layers = new int[im.palette.length][ah][aw];
		
		for(int j=1;j<ah-1;j++){
			for(int i=1;i<aw-1;i++){
				
				// This pixel"s indexed color
				val = im.array[j][i];
								
				// Are neighbor pixel colors the same?
				if((j>0)&&(i>0)){ n1 = im.array[j-1][i-1]==val?1:0; }else{ n1 =0; }
				if(j>0){ n2 = im.array[j-1][i]==val?1:0; }else{ n2 =0; }
				if((j>0)&&(i<aw-1)){ n3 = im.array[j-1][i+1]==val?1:0; }else{ n3 =0; }
				if(i>0){ n4 = im.array[j][i-1]==val?1:0; }else{ n4 = 0; }
				if(i<aw-1){ n5 = im.array[j][i+1]==val?1:0; }else{ n5 = 0; }
				if((j<ah-1)&&(i>0)){ n6 = im.array[j+1][i-1]==val?1:0; }else{ n6 = 0; }
				if(j<ah-1){ n7 = im.array[j+1][i]==val?1:0; }else{ n7 = 0; }
				if((j<ah-1)&&(i<aw-1)){ n8 = im.array[j+1][i+1]==val?1:0; }else{ n8 = 0; }
				
				// this pixel"s type and looking back on previous pixels
				layers[val][j+1][i+1] = 1 + n5 * 2 + n8 * 4 + n7 * 8 ;
				if(n4==0){ layers[val][j+1][i  ] = 0 + 2 + n7 * 4 + n6 * 8 ; }
				if(n2==0){ layers[val][j  ][i+1] = 0 + n3*2 + n5 * 4 + 8 ; }
				if(n1==0){ layers[val][j  ][i  ] = 0 + n2*2 + 4 + n4 * 8 ; }
				
			}// End of i loop
		}// End of j loop
		
		return layers;
	}// End of layering()
	
	// 3. Walking through an edge node array, discarding edge node types 0 and 15 and creating paths from the rest.
	// Walk directions (dir): 0 > ; 1 ^ ; 2 < ; 3 v  
	// Edge node types ( ▓:light or 1; ░:dark or 0 )
	// ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓
	// ░░  ░░  ░░  ░░  ░▓  ░▓  ░▓  ░▓  ▓░  ▓░  ▓░  ▓░  ▓▓  ▓▓  ▓▓  ▓▓
	// 0   1   2   3   4   5   6   7   8   9   10  11  12  13  14  15
	//
	public static ArrayList<ArrayList<Integer[]>> pathscan (int [][] arr,float pathomit){
		ArrayList<ArrayList<Integer[]>> paths = new ArrayList<ArrayList<Integer[]>>();
		ArrayList<Integer[]> thispath;
		int pacnt=0, px=0,py=0,w=arr[0].length,h=arr.length,dir=0,stepcnt=0,maxsteps=w*h*2;
		boolean pathfinished=true, holepath = false;
		
		for(int j=0;j<h;j++){
			for(int i=0;i<w;i++){
				if((arr[j][i]==0)||(arr[j][i]==15)){// Discard
					stepcnt++;
				}else{// Follow path
					
					// Init
					px = i; py = j;
					paths.add(new ArrayList<Integer[]>());//paths[pacnt] = [];
					thispath = paths.get(paths.size()-1);
					pathfinished = false;
					// fill paths will be drawn, but hole paths are also required to remove unnecessary edge nodes
					if(arr[py][px]==1){dir = 0;}
					if(arr[py][px]==2){dir = 3;}
					if(arr[py][px]==3){dir = 0;}
					if(arr[py][px]==4){dir = 1; holepath = false;}
					if(arr[py][px]==5){dir = 0;}
					if(arr[py][px]==6){dir = 3;}
					if(arr[py][px]==7){dir = 0; holepath = true;}
					if(arr[py][px]==8){dir = 0;}
					if(arr[py][px]==9){dir = 3;}
					if(arr[py][px]==10){dir = 3;}
					if(arr[py][px]==11){dir = 1; holepath = true;}
					if(arr[py][px]==12){dir = 0;}
					if(arr[py][px]==13){dir = 3; holepath = true;}
					if(arr[py][px]==14){dir = 0; holepath = true;}
					// Path points loop
					while(!pathfinished){
						
						// New path point
						thispath.add(new Integer[3]);
						thispath.get(thispath.size()-1)[0] = px;
						thispath.get(thispath.size()-1)[1] = py;
						thispath.get(thispath.size()-1)[2] = arr[py][px];
						
						// Node types
						if(arr[py][px]==1){
							arr[py][px] = 0;
							if(dir==0){
								py--;dir=1; 
							}else if(dir==3){
								px--;dir=2; 
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==2){
							arr[py][px] = 0;
							if(dir==3){
								px++;dir=0; 
							}else if(dir==2){
								py--;dir=1; 
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}
						
						else if(arr[py][px]==3){
							arr[py][px] = 0;
							if(dir==0){
								px++;
							}else if(dir==2){
								px--;
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==4){
							arr[py][px] = 0;
							if(dir==1){
								px++;dir=0; 
							}else if(dir==2){
								py++;dir=3; 
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==5){
							if(dir==0){
								arr[py][px] = 13;py++;dir=3; 
							}else if(dir==1){
								arr[py][px] = 13;px--;dir=2; 
							}else if(dir==2){
								arr[py][px] = 7;py--;dir=1; 
							}else if(dir==3){
								arr[py][px] = 7;px++;dir=0; 
							}
						}

						else if(arr[py][px]==6){
							arr[py][px] = 0;
							if(dir==1){
								py--;
							}else if(dir==3){
								py++;
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}
						
						else if(arr[py][px]==7){
							arr[py][px] = 0;
							if(dir==0){
								py++;dir=3; 
							}else if(dir==1){
								px--;dir=2; 
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==8){
							arr[py][px] = 0;
							if(dir==0){
								py++;dir=3; 
							}else if(dir==1){
								px--;dir=2; 
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==9){
							arr[py][px] = 0;
							if(dir==1){
								py--;
							}else if(dir==3){
								py++;
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==10){
							if(dir==0){
								arr[py][px] = 11;py--;dir=1; 
							}else if(dir==1){
								arr[py][px] = 14;px++;dir=0; 
							}else if(dir==2){
								arr[py][px] = 14;py++;dir=3; 
							}else if(dir==3){
								arr[py][px] = 11;px--;dir=2; 
							}
						}
						
						else if(arr[py][px]==11){
							arr[py][px] = 0;
							if(dir==1){
								px++;dir=0; 
							}else if(dir==2){
								py++;dir=3; 
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==12){
							arr[py][px] = 0;
							if(dir==0){
								px++;
							}else if(dir==2){
								px--;
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==13){
							arr[py][px] = 0;
							if(dir==2){
								py--;dir=1; 
							}else if(dir==3){
								px++;dir=0; 
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}

						else if(arr[py][px]==14){
							arr[py][px] = 0;
							if(dir==0){
								py--;dir=1; 
							}else if(dir==3){
								px--;dir=2; 
							}else{log("Invalid dir "+dir+" on px "+px+" py "+py);pathfinished=true;paths.remove(thispath);}
						}
												
						// Close path
						if((px==thispath.get(0)[0])&&(py==thispath.get(0)[1])){ 
							pathfinished = true;
							pacnt++;
							// Discarding "hole" type paths
							if(holepath){
								paths.remove(thispath);pacnt--;
							}
							// Discarding if path is shorter than pathomit
							if(thispath.size()<pathomit){
								paths.remove(thispath);pacnt--;
							}
						}
						
						// Error: path going out of image
						if((px<0)||(px>=w)||(py<0)||(py>=h)){
							pathfinished = true;
							log("path "+pacnt+" error w "+w+" h "+h+" px "+px+" py "+py);
							paths.remove(thispath);
						}
						
						// Error: stepcnt>maxsteps
						if(stepcnt>maxsteps){
							pathfinished = true;
							log("path "+pacnt+" error stepcnt "+stepcnt+" maxsteps "+maxsteps+" px "+px+" py "+py);
							paths.remove(thispath);
						}
						
						stepcnt++;
						
					}// End of Path points loop
					
				}// End of Follow path
				
			}// End of i loop
		}// End of j loop
		
		return paths;
	}// End of pathscan()

	
	// 3. Batch pathscan
	public static ArrayList<ArrayList<ArrayList<Integer[]>>> batchpathscan (int [][][] layers, float pathomit){
		ArrayList<ArrayList<ArrayList<Integer[]>>> bpaths = new ArrayList<ArrayList<ArrayList<Integer[]>>>();
		for(int k=0; k<layers.length; k++){
			bpaths.add(pathscan(layers[k],pathomit));
		}
		return bpaths;
	}
	
	// 4. interpolating between path points for nodes with 8 directions ( East, SouthEast, S, SW, W, NW, N, NE )
	public static ArrayList<ArrayList<Double[]>> internodes (ArrayList<ArrayList<Integer[]>> paths){
		ArrayList<ArrayList<Double[]>> ins = new ArrayList<ArrayList<Double[]>>();
		ArrayList<Double[]> thisinp;
		Double[] thispoint;
		Integer[] pp1, pp2;
		
		int palen=0,nextidx=0;
		// paths loop
		for(int pacnt=0; pacnt<paths.size(); pacnt++){
			ins.add(new ArrayList<Double[]>());
			thisinp = ins.get(ins.size()-1);
			palen = paths.get(pacnt).size();
			// pathpoints loop
			for(int pcnt=0;pcnt<palen;pcnt++){
			
				// interpolate between two path points
				nextidx = (pcnt+1)%palen;
				thisinp.add(new Double[3]);
				thispoint = thisinp.get(thisinp.size()-1);
				pp1 = paths.get(pacnt).get(pcnt);
				pp2 = paths.get(pacnt).get(nextidx);
				thispoint[0] = (pp1[0]+pp2[0]) / 2.0;
				thispoint[1] = (pp1[1]+pp2[1]) / 2.0;
				
				// line segment direction to the next point
				if(pp1[0]<pp2[0]){ 
					if(pp1[1]<pp2[1]){ thispoint[2] = 1.0; // SouthEast
					}else if(pp1[1]>pp2[1]){ thispoint[2] = 7.0; // NE
					}else{ thispoint[2] = 0.0; } // E
				}else if(pp1[0]>pp2[0]){
					if(pp1[1]<pp2[1]){ thispoint[2] = 3.0; // SW
					}else if(pp1[1]>pp2[1]){ thispoint[2] = 5.0; // NW
					}else{ thispoint[2] = 4.0; } // N
				}else{
					if(pp1[1]<pp2[1]){ thispoint[2] = 2.0; // S
					}else if(pp1[1]>pp2[1]){ thispoint[2] = 6.0; // N
					}else{ thispoint[2] = 8.0; }// center, this should not happen
				}
				
			}// End of pathpoints loop 
						
		}// End of paths loop
		
		return ins;
	}// End of internodes()	
	
	// 4. Batch interpollation
	static ArrayList<ArrayList<ArrayList<Double[]>>> batchinternodes (ArrayList<ArrayList<ArrayList<Integer[]>>> bpaths){
		ArrayList<ArrayList<ArrayList<Double[]>>> binternodes = new ArrayList<ArrayList<ArrayList<Double[]>>>();
		for(int k=0; k<bpaths.size(); k++) {
			binternodes.add(internodes(bpaths.get(k)));
		}
		return binternodes;
	}
	
	// 5. tracepath() : recursively trying to fit straight and quadratic spline segments on the 8 direction internode path
	
	// 5.1. Find sequences of points with only 2 segment types
	// 5.2. Fit a straight line on the sequence
	// 5.3. If the straight line fails (an error>ltreshold), find the point with the biggest error
	// 5.4. Fit a quadratic spline through errorpoint (project this to get controlpoint), then measure errors on every point in the sequence
	// 5.5. If the spline fails (an error>qtreshold), find the point with the biggest error
	// 5.6. Set splitpoint = (fitting point + errorpoint)/2
	// 5.7. Split sequence and recursively apply 3. - 7. to startpoint-splitpoint and splitpoint-endpoint sequences
	// 5.8. TODO? If splitpoint-endpoint is a spline, try to add new points from the next sequence
	
	// This returns an SVG Path segment as a double[7] where
	// segment[0] ==1.0 linear  ==2.0 quadratic interpolation
	// segment[1] , segment[2] : x1 , y1
	// segment[3] , segment[4] : x2 , y2 ; middle point of Q curve, endpoint of L line
	// segment[5] , segment[6] : x3 , y3 for Q curve, should be 0.0 , 0.0 for L line 
	//
	// path type is discarded, no check for path.size < 3 , which should not happen
	
	public static ArrayList<Double[]> tracepath (ArrayList<Double[]> path, float ltreshold, float qtreshold){
		int pcnt=0, seqend=0; double segtype1, segtype2;
		ArrayList<Double[]> smp = new ArrayList<Double[]>();
		Double [] thissegment;
		while(pcnt<path.size()){
			// 5.1. Find sequences of points with only 2 segment types
			segtype1 = path.get(pcnt)[2]; segtype2 = -1; seqend=pcnt+1;
			while( ((path.get(seqend)[2]==segtype1) || (path.get(seqend)[2]==segtype2) || (segtype2==-1)) && (seqend<path.size()-1)){
				if((path.get(seqend)[2]!=segtype1) && (segtype2==-1)){ segtype2 = path.get(seqend)[2];}
				seqend++;
			}
			
			// TODO: discard first seqence part, remember the start of the second sequence, wrap over the last sequence
			
			// 5.2. - 5.7. Split sequence and recursively apply 3. - 7. to startpoint-splitpoint and splitpoint-endpoint sequences
			smp.addAll(fitseq(path,ltreshold,qtreshold,pcnt,seqend));
			// 5.8. TODO? If splitpoint-endpoint is a spline, try to add new points from the next sequence
			
			// forward pcnt;
			pcnt = seqend;
			
			// check if there are enough remaining points
			if(pcnt>path.size()-3){
				if(pcnt==path.size()-2){
					smp.add(new Double[7]);
					thissegment = smp.get(smp.size()-1);
					thissegment[0] = 2.0;
					thissegment[1] = path.get(pcnt)[0];
					thissegment[2] = path.get(pcnt)[1];
					thissegment[3] = path.get(path.size()-1)[0];
					thissegment[4] = path.get(path.size()-1)[1];
					thissegment[5] = path.get(0)[0];
					thissegment[6] = path.get(0)[1];
					pcnt = path.size();
				}else{
					smp.add(new Double[7]);
					thissegment = smp.get(smp.size()-1);
					thissegment[0] = 1.0;
					thissegment[1] = path.get(pcnt)[0];
					thissegment[2] = path.get(pcnt)[1];
					thissegment[3] = path.get(0)[0];
					thissegment[4] = path.get(0)[1];
					thissegment[5] = 0.0;
					thissegment[6] = 0.0;
					pcnt = path.size();
				}
			}// End of remaining points check
			
		}// End of pcnt loop
		
		return smp;
		
	}// End of tracepath()
	
	// 5.2. - 5.7. recursively fitting a straight or quadratic line segment on this sequence of path nodes, 
	// called from tracepath()
	public static ArrayList<Double[]> fitseq (ArrayList<Double[]> path, float ltreshold, float qtreshold, int seqstart, int seqend){
		ArrayList<Double[]> segment = new ArrayList<Double[]>();
		Double [] thissegment;
		// return if 0 length
		if(seqstart>=seqend){return segment;}
		
		int errorpoint=seqstart;
		boolean curvepass=true;
		double px, py, dist2, errorval=0;
		double vx = (double)(path.get(seqend)[0]-path.get(seqstart)[0]) / (double)(seqend-seqstart), 
			   vy = (double)(path.get(seqend)[1]-path.get(seqstart)[1]) / (double)(seqend-seqstart);
		
		// 5.2. Fit a straight line on the sequence
		for(int pcnt=seqstart+1;pcnt<seqend;pcnt++){
			px = path.get(seqstart)[0] + vx * (pcnt-seqstart); py = path.get(seqstart)[1] + vy * (pcnt-seqstart);
			dist2 = (path.get(pcnt)[0]-px)*(path.get(pcnt)[0]-px) + (path.get(pcnt)[1]-py)*(path.get(pcnt)[1]-py);
			if(dist2>ltreshold){curvepass=false;}
			if(dist2>errorval){ errorpoint=pcnt; errorval=dist2; }
			pcnt++;
		}
		// return straight line if fits
		if(curvepass){
			segment.add(new Double[7]);
			thissegment = segment.get(segment.size()-1);
			thissegment[0] = 1.0;
			thissegment[1] = path.get(seqstart)[0];
			thissegment[2] = path.get(seqstart)[1];
			thissegment[3] = path.get(seqend)[0];
			thissegment[4] = path.get(seqend)[1];
			thissegment[5] = 0.0;
			thissegment[6] = 0.0;
			return segment;
		}
		
		// 5.3. If the straight line fails (an error>ltreshold), find the point with the biggest error
		int fitpoint = errorpoint; curvepass = true; errorval = 0;
		
		// 5.4. Fit a quadratic spline through this point, measure errors on every point in the sequence
		// helpers and projecting to get control point
		double t=(double)(fitpoint-seqstart)/(double)(seqend-seqstart), t1=(1.0-t)*(1.0-t), t2=2.0*(1.0-t)*t, t3=t*t;
		double cpx = (t1*path.get(seqstart)[0] + t3*path.get(seqend)[0] - path.get(fitpoint)[0])/-t2 ,
			cpy = (t1*path.get(seqstart)[1] + t3*path.get(seqend)[1] - path.get(fitpoint)[1])/-t2 ;
		// Check every point
		for(int pcnt=seqstart+2;pcnt<seqend-1;pcnt++){
			
			t=(double)(pcnt-seqstart)/(double)(seqend-seqstart); t1=(1.0-t)*(1.0-t); t2=2.0*(1.0-t)*t; t3=t*t;
			px = t1*path.get(seqstart)[0]+t2*cpx+t3*path.get(seqend)[0]; py = t1*path.get(seqstart)[1]+t2*cpy+t3*path.get(seqend)[1];
			
			dist2 = (path.get(pcnt)[0]-px)*(path.get(pcnt)[0]-px) + (path.get(pcnt)[1]-py)*(path.get(pcnt)[1]-py);
			
			if(dist2>qtreshold){curvepass=false;}
			if(dist2>errorval){ errorpoint=pcnt; errorval=dist2; }
			pcnt++;
		}
		// return spline if fits
		if(curvepass){
			segment.add(new Double[7]);
			thissegment = segment.get(segment.size()-1);
			thissegment[0] = 2.0;
			thissegment[1] = path.get(seqstart)[0];
			thissegment[2] = path.get(seqstart)[1];
			thissegment[3] = cpx;
			thissegment[4] = cpy;
			thissegment[5] = path.get(seqend)[0];
			thissegment[6] = path.get(seqend)[1];
			return segment; 
		}
		
		// 5.5. If the spline fails (an error>qtreshold), find the point with the biggest error
		// 5.6. Set splitpoint = (fitting point + errorpoint)/2
		int splitpoint = (int)((fitpoint + errorpoint)/2);
		
		// 5.7. Split sequence and recursively apply 5.2. - 5.6. to startpoint-splitpoint and splitpoint-endpoint sequences
		segment = fitseq(path,ltreshold,qtreshold,seqstart,splitpoint);
		segment.addAll(fitseq(path,ltreshold,qtreshold,splitpoint,seqend));
		return segment;
		
	}// End of fitseq()
	
	// 5. Batch tracing paths
	public static ArrayList<ArrayList<Double[]>> batchtracepaths(ArrayList<ArrayList<Double[]>> internodepaths, float ltres,float qtres){
		ArrayList<ArrayList<Double[]>> btracedpaths = new ArrayList<ArrayList<Double[]>>(); 
		for(int k=0;k<internodepaths.size();k++){
			btracedpaths.add(tracepath(internodepaths.get(k),ltres,qtres) ); 
		}
		return btracedpaths;
	}
	
	// 5. Batch tracing layers
	public static ArrayList<ArrayList<ArrayList<Double[]>>> batchtracelayers(ArrayList<ArrayList<ArrayList<Double[]>>> binternodes, float ltres, float qtres){
		ArrayList<ArrayList<ArrayList<Double[]>>> btbis = new ArrayList<ArrayList<ArrayList<Double[]>>>();
		for(int k=0; k<binternodes.size(); k++){
			btbis.add( batchtracepaths( binternodes.get(k),ltres,qtres) );
		}
		return btbis;
	}
	
	////////////////////////////////////////////////////////////
	//
	//  SVG Drawing functions
	//
	////////////////////////////////////////////////////////////
	
	// Getting SVG path element string from a traced path
	public static void svgpathstring(StringBuilder sb, String desc, ArrayList<Double[]> segments, String fillcolor, float scale, float lcpr, float qcpr){
		// Path
		sb.append("<path fill=\"").append(fillcolor).append("\" stroke=\"").append(fillcolor).append("\" stroke-width=\"").append(1).append("\" desc=\"").append(desc).append("\" d=\"" ).append(
				"M").append(segments.get(0)[1]*scale).append(" ").append(segments.get(0)[2]*scale).append(" ");
		
		for(int pcnt=0;pcnt<segments.size();pcnt++){
			if(segments.get(pcnt)[0]==1.0){
				sb.append("L ").append(segments.get(pcnt)[3]*scale).append(" ").append(segments.get(pcnt)[4]*scale).append(" ");
			}else{
				sb.append("Q ").append(segments.get(pcnt)[3]*scale).append(" ").append(segments.get(pcnt)[4]*scale).append(" ").append(segments.get(pcnt)[5]*scale).append(" ").append(segments.get(pcnt)[6]*scale).append(" ");
			}
		}
		sb.append("Z \" />");
		
		// Rendering control points
		if((lcpr>0)&&(qcpr>0)){
			for(int pcnt=0;pcnt<segments.size();pcnt++){
				if(segments.get(pcnt)[0]==2.0){ 
					sb.append( "<circle cx=\"").append(segments.get(pcnt)[3]*scale).append("\" cy=\"").append(segments.get(pcnt)[4]*scale).append("\" r=\"").append(qcpr).append("\" fill=\"cyan\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"black\" />");
					sb.append( "<circle cx=\"").append(segments.get(pcnt)[5]*scale).append("\" cy=\"").append(segments.get(pcnt)[6]*scale).append("\" r=\"").append(qcpr).append("\" fill=\"white\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"black\" />");
					sb.append( "<line x1=\"").append(segments.get(pcnt)[1]*scale).append("\" y1=\"").append(segments.get(pcnt)[2]*scale).append("\" x2=\"").append(segments.get(pcnt)[3]*scale).append("\" y2=\"").append(segments.get(pcnt)[4]*scale).append("\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"cyan\" />");
					sb.append( "<line x1=\"").append(segments.get(pcnt)[3]*scale).append("\" y1=\"").append(segments.get(pcnt)[4]*scale).append("\" x2=\"").append(segments.get(pcnt)[5]*scale).append("\" y2=\"").append(segments.get(pcnt)[6]*scale).append("\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"cyan\" />");
				}else{
					sb.append( "<circle cx=\"").append(segments.get(pcnt)[3]*scale).append("\" cy=\"").append(segments.get(pcnt)[4]*scale).append("\" r=\"").append(lcpr).append("\" fill=\"white\" stroke-width=\"").append(lcpr*0.2).append("\" stroke=\"black\" />");
				}
			}
		}else if(lcpr>0){
			for(int pcnt=0;pcnt<segments.size();pcnt++){
				if(segments.get(pcnt)[0]==1.0){
					sb.append( "<circle cx=\"").append(segments.get(pcnt)[3]*scale).append("\" cy=\"").append(segments.get(pcnt)[4]*scale).append("\" r=\"").append(lcpr).append("\" fill=\"white\" stroke-width=\"").append(lcpr*0.2).append("\" stroke=\"black\" />");
				}
			}
		}else if(qcpr>0){
			for(int pcnt=0;pcnt<segments.size();pcnt++){
				if(segments.get(pcnt)[0]==2.0){
					sb.append( "<circle cx=\"").append(segments.get(pcnt)[3]*scale).append("\" cy=\"").append(segments.get(pcnt)[4]*scale).append("\" r=\"").append(qcpr).append("\" fill=\"cyan\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"black\" />");
					sb.append( "<circle cx=\"").append(segments.get(pcnt)[5]*scale).append("\" cy=\"").append(segments.get(pcnt)[6]*scale).append("\" r=\"").append(qcpr).append("\" fill=\"white\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"black\" />");
					sb.append( "<line x1=\"").append(segments.get(pcnt)[1]*scale).append("\" y1=\"").append(segments.get(pcnt)[2]*scale).append("\" x2=\"").append(segments.get(pcnt)[3]*scale).append("\" y2=\"").append(segments.get(pcnt)[4]*scale).append("\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"cyan\" />");
					sb.append( "<line x1=\"").append(segments.get(pcnt)[3]*scale).append("\" y1=\"").append(segments.get(pcnt)[4]*scale).append("\" x2=\"").append(segments.get(pcnt)[5]*scale).append("\" y2=\"").append(segments.get(pcnt)[6]*scale).append("\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"cyan\" />");
				}
			}
		}// End of quadratic control points
		
	}// End of svgpathstring()
	
	
	// Converting tracedata to an SVG string, paths are drawn according to a Z-index 
	// the optional lcpr and qcpr are linear and quadratic control point radiuses 
	public static String getsvgstring(double w, double h, IndexedImage ii, float scale, float lcpr, float qcpr){
		
		// SVG start
		StringBuilder svgstr = new StringBuilder("<svg width=\""+w+"px\" height=\""+h+"px\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" desc=\"Created with ImageTracer.java\" >");

		// Background
		svgstr.append("<rect x=\"0\" y=\"0\" width=\"100%\" height=\"100%\" fill=\""+torgbstr(ii.palette[ii.background])+"\" />");
		
		// creating Z-index
		TreeMap <Double,Integer[]> zindex = new TreeMap <Double,Integer[]>();
		double label;
		// Layer loop
		for(int k=0; k<ii.layers.size(); k++) {
			
			// Path loop
			for(int pcnt=0; pcnt<ii.layers.get(k).size(); pcnt++){
			
				// Label (Z-index key) is the startpoint of the path, linearized
				label = ii.layers.get(k).get(pcnt).get(0)[2] * w + ii.layers.get(k).get(pcnt).get(0)[1];
				// Creating new list if required
				if(!zindex.containsKey(label)){ zindex.put(label,new Integer[2]); }
				// Adding layer and path number to list
				zindex.get(label)[0] = new Integer(k);
				zindex.get(label)[1] = new Integer(pcnt);
			}// End of path loop
			
		}// End of layer loop
		
		// Sorting Z-index is not required, TreeMap is sorted automatically
		
		// Drawing
		// Z-index loop
		for(Entry<Double, Integer[]> entry : zindex.entrySet()) {
			svgpathstring(svgstr,
					"l "+entry.getValue()[0]+" p "+entry.getValue()[1], 
					ii.layers.get(entry.getValue()[0]).get(entry.getValue()[1]),
					torgbstr(ii.palette[entry.getValue()[0]]),
					scale,lcpr,qcpr);
		}
		
		// SVG End
		svgstr.append("</svg>");
		
		return svgstr.toString();
		
	}// End of getsvgstring()
	
	static String torgbstr(byte[] c){
		return "rgb("+(c[0]+128)+","+(c[1]+128)+","+(c[2]+128)+")"; 
	}
	
}// End of ImageTracer class