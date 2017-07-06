# imagetracerjava
![alt Bitmap to Svg](docimages/s1.png)

Simple raster image tracer and vectorizer written in Java for desktop. See https://github.com/jankovicsandras/imagetracerandroid for the Android version.

by András Jankovics

This is a port of imagetracer.js: https://github.com/jankovicsandras/imagetracerjs

### Check this out for a refactored version with better color quantization algorithm: https://github.com/miguelemosreverte/imagetracerjava

### 1.1.2

- minor bugfixes
- lookup based ```pathscan()```

### 1.1.1

- Bugfix: CSS3 RGBA output in SVG was technically incorrect (however supported by major browsers), so this is changed. [More info](https://stackoverflow.com/questions/6042550/svg-fill-color-transparency-alpha)
- transparency support: alpha is not discarded now, it is given more weight in color quantization
- new ```options.roundcoords``` : rounding coordinates to a given decimal place. This can reduce SVG length significantly (>20%) with minor loss of precision.
- new ```options.desc``` : setting this to false will turn off path descriptions, reducing SVG length.
- new ```options.viewbox``` : setting this to true will use viewBox instead of exact width and height
- new ```options.colorsampling``` : color quantization will sample the colors now by default, can be turned off.
- new ```options.blurradius``` : setting this to 1..5 will preprocess the image with a selective Gaussian blur with ```options.blurdelta``` treshold. This can filter noise and improve quality.
- ```IndexedImage``` has width and height
- ```getsvgstring()``` needs now only ```IndexedImage``` (tracedata) and ```options``` as parameters
- ```colorquantization()``` needs now only ```imgd```, ```palette``` and ```options``` as parameters
- background field is removed from the results of color quantization 

### Running as a standalone program 

Warning: if the outfilename parameter is not specified, then this will overwrite <filename>.svg .

Basic usage: 
```bash
java -jar ImageTracer.jar smiley.png
```

With options:
```bash
java -jar ImageTracer.jar smiley.png outfilename output.svg ltres 1 qtres 1 pathomit 8 colorsampling 1 numberofcolors 16 mincolorratio 0.02 colorquantcycles 3 scale 1 simplifytolerance 0 roundcoords 1 lcpr 0 qcpr 0 desc 1 viewbox 0 blurradius 0 blurdelta 20
```

### Including in Java projects
Add ImageTracer.jar to your build path, import, then use the static methods:
```java
import jankovicsandras.imagetracer.ImageTracer;

...

ImageTracer.saveString(
				"output.svg" ,
				ImageTracer.imageToSVG("input.jpg",null,null)
);
```

With options and palette
```java
// Options
HashMap<String,Float> options = new HashMap<String,Float>();

// Tracing
options.put("ltres",1f);
options.put("qtres",1f);
options.put("pathomit",8f);

// Color quantization
options.put("colorsampling",1f); // 1f means true ; 0f means false: starting with generated palette
options.put("numberofcolors",16f);
options.put("mincolorratio",0.02f);
options.put("colorquantcycles",3f);

// SVG rendering
options.put("scale",1f);
options.put("roundcoords",1f); // 1f means rounded to 1 decimal places, like 7.3 ; 3f means rounded to 3 places, like 7.356 ; etc.
options.put("lcpr",0f);
options.put("qcpr",0f);
options.put("desc",1f); // 1f means true ; 0f means false: SVG descriptions deactivated
options.put("viewbox",0f); // 1f means true ; 0f means false: fixed width and height

// Selective Gauss Blur
options.put("blurradius",0f); // 0f means deactivated; 1f .. 5f : blur with this radius
options.put("blurdelta",20f); // smaller than this RGB difference will be blurred

// Palette
// This is an example of a grayscale palette
// please note that signed byte values [ -128 .. 127 ] will be converted to [ 0 .. 255 ] in the getsvgstring function
byte[][] palette = new byte[8][4];
for(int colorcnt=0; colorcnt < 8; colorcnt++){
	palette[colorcnt][0] = (byte)( -128 + colorcnt * 32); // R
	palette[colorcnt][1] = (byte)( -128 + colorcnt * 32); // G
	palette[colorcnt][2] = (byte)( -128 + colorcnt * 32); // B
	palette[colorcnt][3] = (byte)127; 		      // A
}

ImageTracer.saveString(
				"output.svg" ,
				ImageTracer.imageToSVG("input.jpg",options,palette)
);
```

### Deterministic output
See [options for deterministic tracing](https://github.com/jankovicsandras/imagetracerjava/blob/master/deterministic.md)


### Main Functions
|Function name|Arguments|Returns|
|-------------|---------|-------|
|```imageToSVG```|```String filename, HashMap<String,Float> options /*can be null*/, byte [][] palette /*can be null*/```|```String /*SVG content*/```|
|```imageToSVG```|```BufferedImage image, HashMap<String,Float> options /*can be null*/, byte [][] palette /*can be null*/```|```String /*SVG content*/```|
|```imagedataToSVG```|```ImageData imgd, HashMap<String,Float> options /*can be null*/, byte [][] palette /*can be null*/```|```String /*SVG content*/```|
|```imageToTracedata```|```String filename, HashMap<String,Float> options /*can be null*/, byte [][] palette /*can be null*/```|```IndexedImage /*read the source for details*/```|
|```imageToTracedata```|```BufferedImage image, HashMap<String,Float> options /*can be null*/, byte [][] palette /*can be null*/```|```IndexedImage /*read the source for details*/```|
|```imagedataToTracedata```|```ImageData imgd, HashMap<String,Float> options /*can be null*/, byte [][] palette /*can be null*/```|```IndexedImage /*read the source for details*/```|

	
#### Helper Functions
|Function name|Arguments|Returns|
|-------------|---------|-------|
|```saveString```|```String filename, String str```|```void```|
|```loadImageData```|```String filename```|```ImageData /*read the source for details*/```|
|```loadImageData```|```BufferedImage image```|```ImageData /*read the source for details*/```|

```ImageData``` is similar to [ImageData](https://developer.mozilla.org/en-US/docs/Web/API/ImageData) here.

There are more functions for advanced users, read the source if you are interested. :)
	
### Options
|Option name|Default value|Meaning|
|-----------|-------------|-------|
|```ltres```|```1f```|Error treshold for straight lines.|
|```qtres```|```1f```|Error treshold for quadratic splines.|
|```pathomit```|```8f```|Edge node paths shorter than this will be discarded for noise reduction.|
|```colorsampling```|```1f```|Enable or disable color sampling. 1f is on, 0f is off.|
|```numberofcolors```|```16f```|Number of colors to use on palette if pal object is not defined.|
|```mincolorratio```|```0.02f```|Color quantization will randomize a color if fewer pixels than (total pixels*mincolorratio) has it.|
|```colorquantcycles```|```3f```|Color quantization will be repeated this many times.|
|```blurradius```|```0f```|Set this to 1f..5f for selective Gaussian blur preprocessing.|
|```blurdelta```|```20f```|RGBA delta treshold for selective Gaussian blur preprocessing.|
|```scale```|```1f```|Every coordinate will be multiplied with this, to scale the SVG.|
|```roundcoords```|```1f```|rounding coordinates to a given decimal place. 1f means rounded to 1 decimal place like 7.3 ; 3f means rounded to 3 places, like 7.356|
|```viewbox```|```0f```|Enable or disable SVG viewBox. 1f is on, 0f is off.|
|```desc```|```1f```|Enable or disable SVG descriptions. 1f is on, 0f is off.|
|```lcpr```|```0f```|Straight line control point radius, if this is greater than zero, small circles will be drawn in the SVG. Do not use this for big/complex images.|
|```qcpr```|```0f```|Quadratic spline control point radius, if this is greater than zero, small circles and lines will be drawn in the SVG. Do not use this for big/complex images.|

### Process overview
See [Process overview and Ideas for improvement](https://github.com/jankovicsandras/imagetracerjava/blob/master/process_overview.md)

### License
#### The Unlicense / PUBLIC DOMAIN

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

For more information, please refer to [http://unlicense.org](http://unlicense.org)
