FIJI plugin to identify all the cells in a field by their nuclei then apply the ROI from the nuclei to 2 other colour channels. The 
intensity in the green channel is measured and the brightest spot in the cell located. If the spot is 10 StDev higher than the cell mean
intensity its measured and the same spot region is applied to the red channel. Background measurements are taken at the end of the 
analysis via a user defined region in both the green and red channels.


How to install this plugin
===========================================

1. Make sure you have a working version of CellPose installed on the host computer (https://github.com/MouseLand/cellpose)

2. Copy the Spot_Intensity.jar file into the plugins directory of your FIJI installation.

3. Restart FIJI and SpotIntensity will appear in the list of plugins


How to use this plugin
===========================================
1. Run plugin

2. A dialogue box will open with a number of options.

	(a)Browse to the location of the file to open.
	(b)Set the colours for each channel as they were acquired.
	(c)Select the colour channel with the labelled nuclei (it might work for other large round structures but this isn't tested)
	(d)Set the size of the spot to measure
	(e)Set the Path to Anaconda/Python enviroment (this is usually set when Cellpose is installed).
	(f)Set the path to the Cellpose model, this can be any model capable of working with your image. Plugin was tested with CellposeSAM.
	(g)Set the diameter of the nuclei, if CellposeSAM is used this variable is ignored but its very important for other Cellpose models.

Acknowledgements
===========================================

CellposeSAM is used to segment the nuclei: Pachitariu, M., Rariden, M., & Stringer, C. (2025). Cellpose-SAM: superhuman generalization for cellular segmentation. bioRxiv.



