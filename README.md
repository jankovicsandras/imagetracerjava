# imagetracerjava
Simple raster image tracer and vectorizer written in Java for desktop.

by András Jankovics 2015

This is a port of imagetracer.js , please read that for more details: https://github.com/jankovicsandras/imagetracerjs/blob/master/README.md 

I will try to update this documentation as soon as I have enough time. :)
An Android version is planned also, the only difference will be that Android has no javax.imageio which is used for image loading, so a new loadImageData() function is required.


### Run as a standalone program 

Basic usage: 
```
java -jar ImageTracer.jar <filename>
```

With options:
```
java -jar ImageTracer.jar <filename> ltres 1 qtres 1 pathomit 8 numberofcolors 16 mincolorratio 0.02 colorquantcycles 3 scale 1 lcpr 0 qcpr 0
```

### Initialization
Include ImageTracer.java in your project, then create an ImageTracer object:
```
ImageTracer it = new ImageTracer();
```

### Examples
#### Example 1
Basic usage: loading image, tracing, creating SVG string, saving SVG string as a new file
```
saveString("output.svg",
    imageToSVG("input.jpg",null)
);
```

#### Example 2
With options
```
HashMap<String,Float> options = new HashMap<String,Float>();

// Tracing
options.put("ltres",1f);
options.put("qtres",1f);
options.put("pathomit",8f);

// Color quantization
options.put("numberofcolors",16f);
options.put("mincolorratio",0.02f);
options.put("colorquantcycles",3f);

// SVG rendering
options.put("scale",1f);
options.put("lcpr",0f);
options.put("qcpr",0f);

saveString("output.svg",
    imageToSVG("input.jpg",options)
);
```

There are more functions for advanced users, read the source if you are interested. :)
	
### Options
|Option name|Default value|Meaning|
|-----------|-------------|-------|
|ltres|1|Error treshold for straight lines. Use 0.001 instead of 0 if only round splines are required.|
|qtres|1|Error treshold for quadratic splines. Use 0.001 instead of 0 if only straight lines are required.|
|pathomit|8|Edge node paths shorter than this will be discarded for noise reduction. Use 1 instead of 0 to get paths around single pixels.|
|numberofcolors|16|Number of colors to use on palette if pal object is not defined.|
|mincolorratio|0.02|Color quantization will randomize a color if fewer pixels than (total pixels*mincolorratio) has it.|
|colorquantcycles|3|Color quantization will be repeated this many times.|
|scale|1|Every coordinate will be multiplied with this, to scale the SVG.|
|lcpr|0|Straight line control point radius, if this is greater than zero, small circles will be drawn in the SVG. Do not use this for big/complex images.|
|qcpr|0|Quadratic spline control point radius, if this is greater than zero, small circles and lines will be drawn in the SVG. Do not use this for big/complex images.|

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
