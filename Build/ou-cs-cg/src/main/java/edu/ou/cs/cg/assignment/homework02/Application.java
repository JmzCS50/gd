//******************************************************************************
// Copyright (C) 2016-2022 University of Oklahoma Board of Trustees.
//******************************************************************************
// Last modified: Wed Feb  9 11:33:04 2022 by Chris Weaver
//******************************************************************************
// Major Modification History:
//
// 20160209 [weaver]:	Original file.
// 20190129 [weaver]:	Updated to JOGL 2.3.2 and cleaned up.
// 20190203 [weaver]:	Additional cleanup and more extensive comments.
// 20190206 [weaver]:	Heavily reduced version of old Homework 02 solution.
// 20200121 [weaver]:	Modified to set up OpenGL and UI on the Swing thread.
// 20201215 [weaver]:	Added setIdentifyPixelScale() to canvas setup.
// 20210209 [weaver]:	Added point smoothing for Hi-DPI displays.
// 20220209 [weaver]:	Additional cleanup.
//
//******************************************************************************
// Notes:
//
// Warning! This code uses deprecated features of OpenGL, including immediate
// mode vertex attribute specification, for sake of easier classroom learning.
// See www.khronos.org/opengl/wiki/Legacy_OpenGL
//
//******************************************************************************

package edu.ou.cs.cg.assignment.homework02;

//import java.lang.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import javax.swing.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.*;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;

//******************************************************************************

/**
 * The <CODE>Application</CODE> class.<P>
 *
 * @author  Chris Weaver
 * @version %I%, %G%
 */
public final class Application
	implements GLEventListener, Runnable
{
	//**********************************************************************
	// Private Class Members
	//**********************************************************************

	private static final String		DEFAULT_NAME = "Homework02";
	private static final Dimension		DEFAULT_SIZE = new Dimension(1280, 720);

	//**********************************************************************
	// Public Class Members
	//**********************************************************************

	public static final GLUT			MYGLUT = new GLUT();
	public static final Random			RANDOM = new Random();

	//**********************************************************************
	// Private Members
	//**********************************************************************

	// State (internal) variables
	// Define constants for the Lorenz attractor
    private static final double sigma = 10.0;
    private static final double rho = 28.0;
    private static final double beta = 8.0 / 3.0;
    // Private class member to hold the attractor points
	private ArrayList<float[]> lorenzPoints;
    // Define scale for galaxy visualization
    private static final double scale = 10.0; // Adjust this value as needed

	private float shadowXOffset = 0; // Initial shadow X offset
	private float shadowScale = 1.0f; // Initial shadow scale
	private float moonColorR = 1.0f; // Initial moon red color component
	private float shadowDirection = 1; 

	private int				w;				// Canvas width
	private int				h;				// Canvas height
	private int				k = 0;			// Animation counter
	private TextRenderer		renderer;

	private float				thickline;		// Line thickness
	private boolean			fillpolys;		// Fill polygons?

	//**********************************************************************
	// Main
	//**********************************************************************

	public static void	main(String[] args)
	{
		SwingUtilities.invokeLater(new Application(args));
	}

	//**********************************************************************
	// Constructors and Finalizer
	//**********************************************************************

	public Application(String[] args)
	{
	}

	//**********************************************************************
	// Override Methods (Runnable)
	//**********************************************************************

	public void	run()
	{
		GLProfile		profile = GLProfile.getDefault();

		System.out.println("Running on Java version " + 
			System.getProperty("java.version"));
		System.out.println("Running with OpenGL version " +
			profile.getName());

		GLCapabilities	capabilities = new GLCapabilities(profile);
		GLCanvas		canvas = new GLCanvas(capabilities);	// Single-buffer
		//GLJPanel		canvas = new GLJPanel(capabilities);	// Double-buffer
		JFrame			frame = new JFrame(DEFAULT_NAME);

		// Rectify display scaling issues when in Hi-DPI mode on macOS.
		edu.ou.cs.cg.utilities.Utilities.setIdentityPixelScale(canvas);

		// Specify the starting width and height of the canvas itself
		canvas.setPreferredSize(DEFAULT_SIZE);

		// Populate and show the frame
		frame.setBounds(50, 50, 200, 200);
		frame.getContentPane().add(canvas);
		frame.pack();
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		// Exit when the user clicks the frame's close button
		frame.addWindowListener(new WindowAdapter() {
				public void windowClosing(WindowEvent e) {
					System.exit(0);
				}
			});

		// Register this class to update whenever OpenGL needs it
		canvas.addGLEventListener(this);

		// Have OpenGL call display() to update the canvas 60 times per second
		FPSAnimator	animator = new FPSAnimator(canvas, 60);

		animator.start();
	}

	//**********************************************************************
	// Override Methods (GLEventListener)
	//**********************************************************************

	// Called immediately after the GLContext of the GLCanvas is initialized.
	
	public void init(GLAutoDrawable drawable) {
		// Get the GL2 object from the drawable to perform OpenGL operations
		GL2 gl = drawable.getGL().getGL2();

		// Set the window (viewport) dimensions
		w = drawable.getSurfaceWidth();
		h = drawable.getSurfaceHeight();
		generateLorenzAttractor();
		// Initialize the TextRenderer for displaying text
		renderer = new TextRenderer(new Font("Serif", Font.PLAIN, 18), true, true);

		// Initialize the rendering pipeline
		initPipeline(drawable);

		// Set the clear color to a color of your choice (e.g., black)
		gl.glClearColor(1.0f, 1.0f, 1.0f, 0.0f); // White background


		// Set up the projection matrix to an orthographic view
		setProjection(gl);

		// Enable blending for smooth color transitions (e.g., for the sky and ground)
		gl.glEnable(GL2.GL_BLEND);
		gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

		// Enable anti-aliasing if desired
		gl.glEnable(GL2.GL_LINE_SMOOTH);
		gl.glHint(GL2.GL_LINE_SMOOTH_HINT, GL2.GL_NICEST);
		gl.glEnable(GL2.GL_POLYGON_SMOOTH);
		gl.glHint(GL2.GL_POLYGON_SMOOTH_HINT, GL2.GL_NICEST);

		// Set up depth testing if you're going to use 3D objects
		gl.glClearDepth(1.0f);
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glDepthFunc(GL2.GL_LEQUAL);
		
	}


	// Notification to release resources for the GLContext.
	public void	dispose(GLAutoDrawable drawable)
	{
		renderer = null;
	}

	// Called to initiate rendering of each frame into the GLCanvas.
	// Main display method
		@Override
	public void display(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();

		// Clear the screen
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

		// Draw the galaxy background first
		gl.glPushMatrix(); // Save the current state
		gl.glPopMatrix(); // Restore the state

		// Then set the projection for the rest of the scene
		setProjection(gl);
		
		// Reset to Modelview Matrix
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();

		// Draw the rest of the scene
		drawSky(gl);
		
		drawGround(gl);
		drawLorenzAttractor(gl);
		drawHouse1(gl);
		drawHouse2(gl);
		drawHouse3(gl);
		drawComplexHouse(gl, 600, 457);
		// drawKite(gl);
		drawFences(gl, 200, 100);
		animateFlag(gl);
		drawMoonWithShadow(gl, 1200, 800);
		
		
		gl.glFlush(); // Flush the drawing to the screen
	}
	
	// Called during the first repaint after a resize of the GLCanvas.
	public void	reshape(GLAutoDrawable drawable, int x, int y, int w, int h)
	{
		this.w = w;
		this.h = h;
	}

	//**********************************************************************
	// Private Methods (Rendering)
	//**********************************************************************

	// Update the scene model for the current animation frame.
	private void	update(GLAutoDrawable drawable)
	{
		k++;									// Advance animation counter

		if (k < 180)							// Don't change for 3s at start
			return;

		thickline = 0.5f * ((k / 15) % 12);	// +0.5 per 0.25s max 6, reset
		fillpolys = ((k / 180) % 2 == 0);		// Toggle filling every 3s
	}

	// Render the scene model and display the current animation frame.
	private void render(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();

		// Set the clear color to white
		gl.glClearColor(1.0f, 1.0f, 1.0f, 0.0f);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT); // Clear the buffer

		setProjection(gl); // Use screen coordinates

		// Draw the updated skyscrapers
		drawHouse1(gl);
		drawHouse2(gl);
		drawHouse3(gl);
	}

	//**********************************************************************
	// Private Methods (Pipeline)
	//**********************************************************************

	// www.khronos.org/registry/OpenGL-Refpages/es2.0/xhtml/glBlendFunc.xml
	private void	initPipeline(GLAutoDrawable drawable)
	{
		GL2	gl = drawable.getGL().getGL2();

		// Make points easier to see on Hi-DPI displays
		gl.glEnable(GL2.GL_POINT_SMOOTH);	// Turn on point anti-aliasing
	}

	// Position and orient the default camera to view in 2-D, in pixel coords.
	private void	setProjection(GL2 gl)
	{
		GLU	glu = GLU.createGLU();

		gl.glMatrixMode(GL2.GL_PROJECTION);		// Prepare for matrix xform
		gl.glLoadIdentity();						// Set to identity matrix
		glu.gluOrtho2D(0.0f, 1280.0f, 0.0f, 720.0f);// 2D translate and scale
	}

	//**********************************************************************
	// Private Methods (Scene)
	//**********************************************************************

	// Warning! Text is drawn in unprojected canvas/viewport coordinates.
	// For more on text rendering, the example on this page is long but helpful:
	// jogamp.org/jogl-demos/src/demos/j2d/FlyingText.java
	private void	drawText(GLAutoDrawable drawable)
	{
		renderer.beginRendering(w, h);
		renderer.setColor(0.75f, 0.75f, 0.75f, 1.0f);
		renderer.draw("Application", 2, h - 14);
		renderer.endRendering();
	}

	// Draw a modern skyscraper.
	private void drawSkyscraper(GL2 gl, int baseX, int baseY, int width, int height, float[] color) {
		// Skyscraper base
		gl.glColor3fv(color, 0); // Set the color for the skyscraper
		gl.glBegin(GL2.GL_QUADS);
		gl.glVertex2i(baseX, baseY);
		gl.glVertex2i(baseX + width, baseY);
		gl.glVertex2i(baseX + width, baseY + height);
		gl.glVertex2i(baseX, baseY + height);
		gl.glEnd();

		// Windows for the skyscraper
		gl.glColor3f(0.1f, 0.1f, 0.1f); // Windows are dark
		int windowRows = height / 50; // Adjust number of rows to match aesthetics
		int windowCols = width / 50; // Adjust number of columns to match aesthetics
		int windowHeight = height / (2 * windowRows);
		int windowWidth = width / (2 * windowCols);
		int windowPaddingY = height / (2 * windowRows + 1);
		int windowPaddingX = width / (2 * windowCols + 1);

		for (int row = 0; row < windowRows; row++) {
			for (int col = 0; col < windowCols; col++) {
				int windowX = baseX + windowPaddingX + col * (windowWidth + windowPaddingX);
				int windowY = baseY + windowPaddingY + row * (windowHeight + windowPaddingY);
				gl.glBegin(GL2.GL_QUADS);
				gl.glVertex2i(windowX, windowY);
				gl.glVertex2i(windowX + windowWidth, windowY);
				gl.glVertex2i(windowX + windowWidth, windowY + windowHeight);
				gl.glVertex2i(windowX, windowY + windowHeight);
				gl.glEnd();
			}
		}
	}


// Draw the parts of a modern skyscraper instead of a house.
private void drawHouse1(GL2 gl) {
    float[] color = new float[]{0.7f, 0.7f, 0.7f}; // Light gray color
    drawSkyscraper(gl, 108, 132, 200, 500, color); // Adjust width and height as needed
}

private void drawHouse2(GL2 gl) {
    float[] color = new float[]{0.5f, 0.5f, 0.5f}; // Medium gray color
    drawSkyscraper(gl, 634, 158, 150, 450, color); // Adjust width and height as needed
}

private void drawHouse3(GL2 gl) {
    int baseX = 1048; // Base X coordinate
    int baseY = 132;  // Base Y coordinate
    int houseWidth = 180; // House width
    int houseHeight = 200; // House height
    float[] houseColor = new float[]{0.7f, 0.0f, 0.0f}; // Darker shade of red

    // Draw the main body of the house
    gl.glColor3fv(houseColor, 0); // Set house color
    gl.glBegin(GL2.GL_QUADS);
    gl.glVertex2f(baseX, baseY);
    gl.glVertex2f(baseX + houseWidth, baseY);
    gl.glVertex2f(baseX + houseWidth, baseY + houseHeight);
    gl.glVertex2f(baseX, baseY + houseHeight);
    gl.glEnd();

    // Draw the roof
    float roofHeight = 50; // Additional height for the roof
    gl.glColor3f(0.5f, 0.3f, 0.0f); // Brown color for the roof
    gl.glBegin(GL2.GL_TRIANGLES);
    gl.glVertex2f(baseX, baseY + houseHeight);
    gl.glVertex2f(baseX + houseWidth / 2.0f, baseY + houseHeight + roofHeight); // Peak of the roof
    gl.glVertex2f(baseX + houseWidth, baseY + houseHeight);
    gl.glEnd();

    // Draw the door
    float doorWidth = 50; // Door width
    float doorHeight = 100; // Door height
    gl.glColor3f(0.2f, 0.1f, 0.0f); // Dark brown color for the door
    gl.glBegin(GL2.GL_QUADS);
    gl.glVertex2f(baseX + (houseWidth - doorWidth) / 2.0f, baseY);
    gl.glVertex2f(baseX + (houseWidth + doorWidth) / 2.0f, baseY);
    gl.glVertex2f(baseX + (houseWidth + doorWidth) / 2.0f, baseY + doorHeight);
    gl.glVertex2f(baseX + (houseWidth - doorWidth) / 2.0f, baseY + doorHeight);
    gl.glEnd();

    // Draw the window
    float windowWidth = 40; // Window width
    float windowHeight = 40; // Window height
    gl.glColor3f(0.7f, 0.9f, 1.0f); // Light blue color for the window
    gl.glBegin(GL2.GL_QUADS);
    gl.glVertex2f(baseX + houseWidth - windowWidth - 20, baseY + 120);
    gl.glVertex2f(baseX + houseWidth - 20, baseY + 120);
    gl.glVertex2f(baseX + houseWidth - 20, baseY + 120 + windowHeight);
    gl.glVertex2f(baseX + houseWidth - windowWidth - 20, baseY + 120 + windowHeight);
    gl.glEnd();
}


	// Draw entire chimney, not just the visible part. Effect when fill is off?
	private void	drawChimney(GL2 gl, int dx, int dy)
	{
		setColor(gl, 128, 0, 0);				// Firebrick red
		fillRect(gl, dx, dy, 30, 250);

		setColor(gl, 0, 0, 0);					// Black
		edgeRect(gl, dx, dy, 30, 250);
	}

	// Define five corners of a house frame that is shorter on the left side.
	private static final Point[]	OUTLINE_FRAME = new Point[]
	{
		new Point(  0,   0),		// base, left corner
		new Point(176,   0),		// base, right corner
		new Point(176, 162),		// roof, right corner
		new Point( 88, 250),		// roof, apex
		new Point(  0, 162),		// roof, left corner
	};

	// Draw a house frame, given its lower left corner.
	private void	drawFrame(GL2 gl, int dx, int dy)
	{
		setColor(gl, 128, 64, 0);				// Medium brown
		fillPoly(gl, dx, dy, OUTLINE_FRAME);

		setColor(gl, 0, 0, 0);					// Black
		edgePoly(gl, dx, dy, OUTLINE_FRAME);
	}

	// Draw a door, given its lower left corner.
	private void	drawDoor(GL2 gl, int dx, int dy)
	{
		setColor(gl, 192, 128, 0);				// Light brown
		fillRect(gl, dx, dy, 40, 92);

		setColor(gl, 0, 0, 0);					// Black
		edgeRect(gl, dx, dy, 40, 92);
	}

	// Draw a window, given its center.
	private void	drawWindow(GL2 gl, int dx, int dy)
	{
		int	ww = 20;
		int	hh = 20;

		setColor(gl, 255, 255, 128);			// Light yellow
		fillRect(gl, dx - ww, dy - hh, 2 * ww, 2 * hh);

		setColor(gl, 64, 64, 64);				// Dark gray
		edgeRect(gl, dx - ww, dy - hh, 2 * ww, 2 * hh);
	}

	//**********************************************************************
	// Private Methods (Scene, Fence)
	//**********************************************************************

	private void	drawFences(GL2 gl)
	{
		// Draw a zigzag fence with 8 boards
		fillFenceStrip(gl, 856, 132, 8);
		edgeFenceStrip(gl, 856, 132, 8);

		// Draw a zigzag fence with 2 boards
		fillFenceStrip(gl, 1224, 132, 2);
		edgeFenceStrip(gl, 1224, 132, 2);

		// Draw a rightward-increasing jagged fence with 4 boards
		fillFenceBoard(gl, false,  290, 132);
		edgeFenceBoard(gl, false,  290, 132);
		fillFenceBoard(gl, false,  314, 132);
		edgeFenceBoard(gl, false,  314, 132);
		fillFenceBoard(gl, false,  338, 132);
		edgeFenceBoard(gl, false,  338, 132);
		fillFenceBoard(gl, false,  362, 132);
		edgeFenceBoard(gl, false,  362, 132);
	}

	// Fills a left-to-right sequence of fence boards using a QUAD_STRIP.
	private void	fillFenceStrip(GL2 gl, int dx, int dy, int boards)
	{
		if (!fillpolys)
			return;

		setColor(gl, 192, 192, 128);			// Tan

		gl.glBegin(GL2.GL_QUAD_STRIP);

		gl.glVertex2i(dx + 0, dy + 0);		// base, leftmost slat
		gl.glVertex2i(dx + 0, dy + 102);	// peak, leftmost slat

		for (int i=1; i<=boards; i++)
		{
			int	x = i * 24;
			int	y = ((i % 2 == 1) ? 112 : 102);

			gl.glVertex2i(dx + x, dy + 0);	// base, next slat
			gl.glVertex2i(dx + x, dy + y);	// peak, next slat
		}

		gl.glEnd();
	}

	// Edges a left-to-right sequence of fence boards using LINE_LOOPs.
	private void	edgeFenceStrip(GL2 gl, int dx, int dy, int boards)
	{
		setColor(gl, 0, 0, 0);					// Black

		gl.glLineWidth(thickline);

		for (int i=0; i<boards; i++)
		{
			int	xl = i * 24;
			int	xr = xl + 24;
			int	yl = ((i % 2 == 0) ? 102 : 112);
			int	yr = ((i % 2 == 0) ? 112 : 102);

			gl.glBegin(GL2.GL_LINE_LOOP);

			gl.glVertex2i(dx + xl, dy + 0);	// base, left
			gl.glVertex2i(dx + xr, dy + 0);	// base, right
			gl.glVertex2i(dx + xr, dy + yr);	// peak, right
			gl.glVertex2i(dx + xl, dy + yl);	// peak, left

			gl.glEnd();
		}

		gl.glLineWidth(1.0f);
	}

	// Define four corners of a fence board that is shorter on the left side.
	private static final Point[]	OUTLINE_BOARD_L = new Point[]
	{
		new Point(  0,   0),		// base, left
		new Point( 24,   0),		// base, right
		new Point( 24, 112),		// peak, right
		new Point(  0, 102),		// peak, left
	};

	// Define four corners of a fence board that is shorter on the right side.
	private static final Point[]	OUTLINE_BOARD_R = new Point[]
	{
		new Point(  0,   0),		// base, left
		new Point( 24,   0),		// base, right
		new Point( 24, 102),		// peak, right
		new Point(  0, 112),		// peak, left
	};

	// Fills a single fence slat with bottom left corner at dx, dy.
	// If flip is true, the slat is higher on the left, else on the right.
	private void	fillFenceBoard(GL2 gl, boolean flip, int dx, int dy)
	{
		if (!fillpolys)
			return;

		setColor(gl, 192, 192, 128);			// Tan
		fillPoly(gl, dx, dy, (flip ? OUTLINE_BOARD_R : OUTLINE_BOARD_L));
	}

	// Edges a single fence slat with bottom left corner at dx, dy.
	// If flip is true, the slat is higher on the left, else on the right.
	private void	edgeFenceBoard(GL2 gl, boolean flip, int dx, int dy)
	{
		setColor(gl, 0, 0, 0);					// Black
		edgePoly(gl, dx, dy, (flip ? OUTLINE_BOARD_R : OUTLINE_BOARD_L));
	}

	//**********************************************************************
	// Private Methods (Scene, Kite)
	//**********************************************************************

	private static final int		SIDES_KITE = 18;
	private static final double	ANGLE_KITE = 2.0 * Math.PI / SIDES_KITE;

	// Draws a kite consisting of two fans, one upper blue, one lower red.
	private void	drawKite(GL2 gl)
	{
		int		cx = 956;
		int		cy = 490;
		int		r = 80;

		double		amin =  4.0 * ANGLE_KITE;
		double		amax =  9.0 * ANGLE_KITE;
		double		bmin = 13.0 * ANGLE_KITE;
		double		bmax = 18.0 * ANGLE_KITE;

		int		fans = 5;
		double		astep = (amax - amin) / fans;
		double		bstep = (bmax - bmin) / fans;

		// Fill and edge the lower red fan
		fillKiteFan(gl, cx, cy, fans, r, bmin, bstep);
		edgeKiteFan(gl, cx, cy, fans, r, bmin, bstep);

		for (int i=0; i<fans; i++)
		{
			double	a = amin + astep * i;

			// Fill and edge each upper blue fan blade
			fillKiteBlade(gl, cx, cy, r, a, a + astep);
			edgeKiteBlade(gl, cx, cy, r, a, a + astep);
		}
	}

	// Fills an entire kite fan using a TRIANGLE_FAN.
	private void	fillKiteFan(GL2 gl, int cx, int cy, int fans, int r,
								double min, double step)
	{
		if (!fillpolys)
			return;

		setColor(gl, 224, 80, 48);				// Bright red

		gl.glBegin(GL2.GL_TRIANGLE_FAN);

		gl.glVertex2d(cx, cy);

		for (int i=0; i<=fans; i++)
		{
			double	a = min + step * i;

			gl.glVertex2d(cx + r * Math.cos(a), cy + r * Math.sin(a));
		}

		gl.glEnd();
	}

	// Edges an entire kite fan using a LINE_LOOPs.
	private void	edgeKiteFan(GL2 gl, int cx, int cy, int fans, int r,
								double min, double step)
	{
		setColor(gl, 0, 0, 0);					// Black

		gl.glLineWidth(thickline);

		double	a = min;

		for (int i=0; i<fans; i++)
		{
			gl.glBegin(GL.GL_LINE_LOOP);

			gl.glVertex2d(cx, cy);
			gl.glVertex2d(cx + r * Math.cos(a), cy + r * Math.sin(a));
			a += step;
			gl.glVertex2d(cx + r * Math.cos(a), cy + r * Math.sin(a));

			gl.glEnd();
		}

		gl.glLineWidth(1.0f);
	}

	// Fills a single kite fan blade using a POLYGON.
	private void	fillKiteBlade(GL2 gl, int cx, int cy, int r,
								  double a1, double a2)
	{
		if (!fillpolys)
			return;

		setColor(gl, 48, 80, 224);				// Bright blue

		gl.glBegin(GL2.GL_POLYGON);

		gl.glVertex2d(cx, cy);
		gl.glVertex2d(cx + r * Math.cos(a1), cy + r * Math.sin(a1));
		gl.glVertex2d(cx + r * Math.cos(a2), cy + r * Math.sin(a2));

		gl.glEnd();
	}

	// Edges a single kite fan blade using a LINE_LOOP.
	private void	edgeKiteBlade(GL2 gl, int cx, int cy, int r,
								  double a1, double a2)
	{
		setColor(gl, 0, 0, 0);					// Black

		gl.glLineWidth(thickline);

		gl.glBegin(GL.GL_LINE_LOOP);

		gl.glVertex2d(cx, cy);
		gl.glVertex2d(cx + r * Math.cos(a1), cy + r * Math.sin(a1));
		gl.glVertex2d(cx + r * Math.cos(a2), cy + r * Math.sin(a2));

		gl.glEnd();

		gl.glLineWidth(1.0f);
	}

	//**********************************************************************
	// Private Methods (Convenience Functions)
	//**********************************************************************

	// Sets color, normalizing r, g, b, a values from max 255 to 1.0.
	private void	setColor(GL2 gl, int r, int g, int b, int a)
	{
		gl.glColor4f(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
	}

	// Sets fully opaque color, normalizing r, g, b values from max 255 to 1.0.
	private void	setColor(GL2 gl, int r, int g, int b)
	{
		setColor(gl, r, g, b, 255);
	}

	// Fills a rectangle having lower left corner at (x,y) and dimensions (w,h).
	private void	fillRect(GL2 gl, int x, int y, int w, int h)
	{
		if (!fillpolys)
			return;

		gl.glBegin(GL2.GL_POLYGON);

		gl.glVertex2i(x+0, y+0);
		gl.glVertex2i(x+0, y+h);
		gl.glVertex2i(x+w, y+h);
		gl.glVertex2i(x+w, y+0);

		gl.glEnd();
	}

	// Edges a rectangle having lower left corner at (x,y) and dimensions (w,h).
	private void	edgeRect(GL2 gl, int x, int y, int w, int h)
	{
		gl.glLineWidth(thickline);

		gl.glBegin(GL.GL_LINE_LOOP);

		gl.glVertex2i(x+0, y+0);
		gl.glVertex2i(x+0, y+h);
		gl.glVertex2i(x+w, y+h);
		gl.glVertex2i(x+w, y+0);

		gl.glEnd();

		gl.glLineWidth(1.0f);
	}

	// Fills a polygon defined by a starting point and a sequence of offsets.
	private void	fillPoly(GL2 gl, int startx, int starty, Point[] offsets)
	{
		if (!fillpolys)
			return;

		gl.glBegin(GL2.GL_POLYGON);

		for (int i=0; i<offsets.length; i++)
			gl.glVertex2i(startx + offsets[i].x, starty + offsets[i].y);

		gl.glEnd();
	}

	// Edges a polygon defined by a starting point and a sequence of offsets.
	private void	edgePoly(GL2 gl, int startx, int starty, Point[] offsets)
	{
		gl.glLineWidth(thickline);

		gl.glBegin(GL2.GL_LINE_LOOP);

		for (int i=0; i<offsets.length; i++)
			gl.glVertex2i(startx + offsets[i].x, starty + offsets[i].y);

		gl.glEnd();

		gl.glLineWidth(1.0f);
	}

	
	// Method to draw the sky gradient
	private void drawSky(GL2 gl) {
		gl.glBegin(GL2.GL_QUADS);
		// Top color (dark blue)
		gl.glColor3f(0.0f, 0.0f, 0.3f);
		gl.glVertex2f(0.0f, 720.0f);
		gl.glVertex2f(1280.0f, 720.0f);
		// Bottom color (light blue)
		gl.glColor3f(0.53f, 0.81f, 0.98f);
		gl.glVertex2f(1280.0f, 360.0f);
		gl.glVertex2f(0.0f, 360.0f);
		gl.glEnd();
	}

	// Method to draw the ground gradient
	private void drawGround(GL2 gl) {
		gl.glBegin(GL2.GL_QUADS);
		// Top color (light green)
		gl.glColor3f(0.0f, 0.5f, 0.0f);
		gl.glVertex2f(0.0f, 360.0f);
		gl.glVertex2f(1280.0f, 360.0f);
		// Bottom color (dark green)
		gl.glColor3f(0.0f, 0.25f, 0.0f);
		gl.glVertex2f(1280.0f, 0.0f);
		gl.glVertex2f(0.0f, 0.0f);
		gl.glEnd();
	}


	// Method to draw a simple sun
	private void drawSun(GL2 gl) {
		// Set color to yellow
		gl.glColor3f(0.98f, 0.93f, 0.36f);

		// Calculate the sun's position based on the window's width and height
		float sunX = w - 100; // 100 pixels from the right edge
		float sunY = h - 100; // 100 pixels from the top
		float sunRadius = 50; // Sun's radius

		drawCircle(gl, sunX, sunY, sunRadius); // Draw sun at top-right corner of the scene
	}

	// Helper method to draw a circle
	private void drawCircle(GL2 gl, float cx, float cy, float r) {
		final int num_segments = 100;
		gl.glBegin(GL2.GL_TRIANGLE_FAN);
		for (int i = 0; i <= num_segments; i++) {
			double theta = 2.0 * Math.PI * i / num_segments;
			double x = r * Math.cos(theta);
			double y = r * Math.sin(theta);
			gl.glVertex2d(x + cx, y + cy);
		}
		gl.glEnd();
	}


	// Method to draw a simple tree
	private void drawTree(GL2 gl) {
		// Draw trunk
		gl.glColor3f(0.55f, 0.27f, 0.07f);
		gl.glBegin(GL2.GL_QUADS);
		gl.glVertex2f(-0.05f, -0.5f);
		gl.glVertex2f(0.05f, -0.5f);
		gl.glVertex2f(0.05f, -0.3f);
		gl.glVertex2f(-0.05f, -0.3f);
		gl.glEnd();

		// Draw treetop
		gl.glColor3f(0.0f, 0.5f, 0.0f);
		drawCircle(gl, 0.0f, -0.25f, 0.15f); // Draw treetop centered above the trunk
	}

	
	private void drawFlag(GL2 gl, double angle) {
		int flagWidth = 200;
		int flagHeight = 100;
		int poleHeight = 500; // The height of the flag pole
		int divisions = 10; // Number of divisions for the waving effect
		double waveAmplitude = 10.0; // Height of the waves

		// Manually adjust the x position of the flag pole here
		int polePositionX = 320; 

		// Draw the flag pole
		setColor(gl, 139, 69, 19); // Brown color for the pole
		gl.glBegin(GL2.GL_QUADS);
		gl.glVertex2f(polePositionX, 40);
		gl.glVertex2f(polePositionX + 10, 40);
		gl.glVertex2f(polePositionX + 10, 40 + poleHeight);
		gl.glVertex2f(polePositionX, 40 + poleHeight);
		gl.glEnd();

		// Position the flag at the top of the pole
		float flagTop = 100 + poleHeight;
		float flagBottom = flagTop - flagHeight;

		// Draw the flag with a waving effect
		setColor(gl, 255, 0, 0); // Red flag
		for (int i = 0; i < divisions; i++) {
			double x0 = polePositionX + (flagWidth / divisions) * i;
			double x1 = polePositionX + (flagWidth / divisions) * (i + 1);

			// Calculate the wave offset
			double waveOffset = Math.sin(angle + (Math.PI * 2 / divisions) * i) * waveAmplitude;

			gl.glBegin(GL2.GL_QUADS);
			gl.glVertex2f((float)x0, flagTop + (float)waveOffset);
			gl.glVertex2f((float)x1, flagTop + (float)waveOffset);
			gl.glVertex2f((float)x1, flagBottom + (float)waveOffset);
			gl.glVertex2f((float)x0, flagBottom + (float)waveOffset);
			gl.glEnd();
		}
	}




	private void animateFlag(GL2 gl) {
		double angle = Math.sin(System.currentTimeMillis() * 0.001) * 45; // Wave effect
		drawFlag(gl, angle);
	}
	private void drawHouses(GL2 gl) {
		drawHouse1(gl);
		drawHouse2(gl);
		drawHouse3(gl);
	}
	// Method to Generate Lorenz Attractor Points
	private void generateLorenzAttractor() {
		double dt = 0.01;
		double x = 0.01, y = 0, z = 0; // Initial conditions
		int numSteps = 10000; // Number of steps to simulate

		lorenzPoints = new ArrayList<>(numSteps);
		// Variables to track min and max of x and y for normalization
		double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

		// Generate points without scaling
		for (int i = 0; i < numSteps; i++) {
			double dx = sigma * (y - x) * dt;
			double dy = (x * (rho - z) - y) * dt;
			double dz = (x * y - beta * z) * dt;
			x += dx;
			y += dy;
			z += dz;

			// Update min and max
			if (x < minX) minX = x;
			if (x > maxX) maxX = x;
			if (y < minY) minY = y;
			if (y > maxY) maxY = y;

			lorenzPoints.add(new float[]{(float)x, (float)y});
		}

		// Normalize and scale points
		for (int i = 0; i < lorenzPoints.size(); i++) {
			float[] point = lorenzPoints.get(i);
			// Normalize x to [0, 1280]
			float normalizedX = (point[0] - (float)minX) / (float)(maxX - minX) * 1280f;
			// Normalize y to [360, 720]
			float normalizedY = ((point[1] - (float)minY) / (float)(maxY - minY) * 360f) + 360f;
			lorenzPoints.set(i, new float[]{normalizedX, normalizedY});
		}
	}

	private void drawLorenzAttractor(GL2 gl) {
		gl.glColor3f(1.0f, 1.0f, 1.0f); // Set color to white
		gl.glPointSize(1.0f); // Set point size
		gl.glBegin(GL2.GL_POINTS);
		for (float[] point : lorenzPoints) {
			gl.glVertex2f(point[0], point[1]);
		}
		gl.glEnd();
	}

	private void drawComplexHouse(GL2 gl, int dx, int dy) {
		// Base dimensions for scaling
		int houseWidth = 200;
		int houseHeight = 150;
		int roofHeight = 50;
		int doorWidth = 40;
		int doorHeight = 60;
		int windowSize = 30;
		int chimneyWidth = 20;
		int chimneyHeight = 60;
		int starSize = 20;
		int doorX = 700; // X coordinate for the door's position
		int doorY = 156;  // Y coordinate for the door's position

		// Draw divided roof
		gl.glColor3f(0.5f, 0.3f, 0.0f); // Brown
		gl.glBegin(GL2.GL_TRIANGLES);
		gl.glVertex2f(dx, dy + houseHeight);
		gl.glVertex2f(dx + houseWidth / 4, dy + houseHeight + roofHeight);
		gl.glVertex2f(dx + houseWidth / 2, dy + houseHeight);
		gl.glEnd();

		// Draw the other half of the roof
		gl.glBegin(GL2.GL_TRIANGLES);
		gl.glVertex2f(dx + houseWidth / 2, dy + houseHeight);
		gl.glVertex2f(dx + 3 * houseWidth / 4, dy + houseHeight + roofHeight);
		gl.glVertex2f(dx + houseWidth, dy + houseHeight);
		gl.glEnd();

		// Draw window with shades
		gl.glColor3f(0.7f, 0.7f, 1.0f); // Light blue for window
		gl.glBegin(GL2.GL_QUADS);
		gl.glVertex2f(dx + 51, dy + houseHeight - 11);
		gl.glVertex2f(dx + 51 + windowSize, dy + houseHeight - 11);
		gl.glVertex2f(dx + 51 + windowSize, dy + houseHeight - windowSize - 11);
		gl.glVertex2f(dx + 51, dy + houseHeight - windowSize - 11);
		gl.glEnd();

			// Drawing the door
		gl.glColor3f(0.3f, 0.2f, 0.1f); // Color: dark brown
		gl.glBegin(GL2.GL_QUADS);
		gl.glVertex2f(doorX, doorY);
		gl.glVertex2f(doorX + doorWidth, doorY);
		gl.glVertex2f(doorX + doorWidth, doorY + doorHeight);
		gl.glVertex2f(doorX, doorY + doorHeight);
		gl.glEnd();

		// Drawing the doorknob
		float knobX = doorX + doorWidth * 0.75f; // X position of the doorknob
		float knobY = doorY + doorHeight * 0.5f; // Y position of the doorknob, centered vertically
		gl.glColor3f(1.0f, 1.0f, 0.0f); // Color: yellow
		gl.glPointSize(5.0f); // Set the point size for the doorknob
		gl.glBegin(GL2.GL_POINTS);
		gl.glVertex2f(knobX, knobY);
		gl.glEnd();

		// Draw a window with a thicker frame
		// Assuming the window with shades is the one we want to have a thicker frame
		gl.glColor3f(0.0f, 0.0f, 0.0f); // Black for frame
		gl.glLineWidth(3.0f);
		gl.glBegin(GL2.GL_LINE_LOOP);
		gl.glVertex2f(dx + 51, dy + houseHeight - 11);
		gl.glVertex2f(dx + 51 + windowSize, dy + houseHeight - 11);
		gl.glVertex2f(dx + 51 + windowSize, dy + houseHeight - 11 - windowSize);
		gl.glVertex2f(dx + 51, dy + houseHeight - 11 - windowSize);
		gl.glEnd();

		// Draw smokestack with smoke
		// Smokestack
		gl.glColor3f(0.5f, 0.5f, 0.5f); // Grey for chimney
		gl.glBegin(GL2.GL_QUADS);
		gl.glVertex2f(dx + 20, dy + houseHeight);
		gl.glVertex2f(dx + 20 + chimneyWidth, dy + houseHeight);
		gl.glVertex2f(dx + 20 + chimneyWidth, dy + houseHeight + chimneyHeight);
		gl.glVertex2f(dx + 20, dy + houseHeight + chimneyHeight);
		gl.glEnd();
		// Smoke (simplified as circles)
		gl.glColor4f(0.8f, 0.8f,0.8f, 0.5f); // Light grey for smoke, semi-transparent
		gl.glPointSize(10.0f);
		gl.glBegin(GL2.GL_POINTS);
		gl.glVertex2f(dx + 30, dy + houseHeight + chimneyHeight + 10); // First smoke cloud
		gl.glVertex2f(dx + 35, dy + houseHeight + chimneyHeight + 20); // Second smoke cloud
		gl.glVertex2f(dx + 25, dy + houseHeight + chimneyHeight + 30); // Third smoke cloud
		gl.glEnd();

		// Draw a five-pointed star
		gl.glColor3f(1.0f, 1.0f, 0.0f); // Yellow for the star
		float starCenterX = dx + houseWidth * 0.75f;
		float starCenterY = dy + houseHeight + roofHeight + 20; // Position above the roof
		float starRadius = starSize / 2.0f;
		gl.glBegin(GL2.GL_TRIANGLE_FAN);
		for (int i = 0; i <= 10; i++) {
		float angle = (float) i / 5.0f * (float) Math.PI;
		float x = starCenterX + (float) Math.sin(angle) * starRadius * (i % 2 == 0 ? 0.4f : 1.0f);
		float y = starCenterY + (float) Math.cos(angle) * starRadius * (i % 2 == 0 ? 0.4f : 1.0f);
		gl.glVertex2f(x, y);
		}
		gl.glEnd();

		// Reset point size and line width to default
		gl.glPointSize(1.0f);
		gl.glLineWidth(1.0f);
	}

	private void drawFences(GL2 gl, int numBoards1, int numBoards2) {
		// Draw jagged fence
		float startX1 = 0; // Starting X coordinate for the jagged fence
		float startY1 = 50; // Starting Y coordinate for the jagged fence
		float boardWidth1 = 20; // Width of each board for the jagged fence
		float boardHeight1 = 100; // Height of each board for the jagged fence
		float gap1 = 5; // Gap between boards for the jagged fence

		gl.glColor3f(0.6f, 0.4f, 0.2f); // Brown color for the jagged fence

		for (int i = 0; i < numBoards1; i++) {
			gl.glBegin(GL2.GL_QUADS);
			gl.glVertex2f(startX1 + i * (boardWidth1 + gap1), startY1);
			gl.glVertex2f(startX1 + i * (boardWidth1 + gap1) + boardWidth1, startY1);
			gl.glVertex2f(startX1 + i * (boardWidth1 + gap1) + boardWidth1, startY1 + boardHeight1);
			gl.glVertex2f(startX1 + i * (boardWidth1 + gap1), startY1 + boardHeight1);
			gl.glEnd();
		}

		// Draw alternating fence
		float startX2 = 300; // Starting X coordinate for the alternating fence
		float startY2 = 50; // Starting Y coordinate for the alternating fence
		float boardWidth2 = 20; // Width of each board for the alternating fence
		float boardHeight2 = 100; // Height of each board for the alternating fence
		float gap2 = 5; // Gap between boards for the alternating fence

		gl.glColor3f(0.6f, 0.4f, 0.2f); // Brown color for the alternating fence

		for (int i = 0; i < numBoards2; i++) {
			if (i % 2 == 0) {
				gl.glColor3f(0.6f, 0.4f, 0.2f); // Brown color for even-numbered boards
			} else {
				gl.glColor3f(0.8f, 0.6f, 0.4f); // Light brown color for odd-numbered boards
			}

			gl.glBegin(GL2.GL_QUADS);
			gl.glVertex2f(startX2 + i * (boardWidth2 + gap2), startY2);
			gl.glVertex2f(startX2 + i * (boardWidth2 + gap2) + boardWidth2, startY2);
			gl.glVertex2f(startX2 + i * (boardWidth2 + gap2) + boardWidth2, startY2 + boardHeight2);
			gl.glVertex2f(startX2 + i * (boardWidth2 + gap2), startY2 + boardHeight2);
			gl.glEnd();
		}
	}
	private void drawMoonWithShadow(GL2 gl, int canvasWidth, int canvasHeight) {
		// Adjust shadow direction when reaching boundaries
		if (shadowXOffset >= 10 || shadowXOffset <= -10) {
			shadowDirection *= -1;
		}
		// Update shadow offset based on direction
		shadowXOffset += 0.1f * shadowDirection;

		// Enable stencil test
		gl.glEnable(GL2.GL_STENCIL_TEST);

		// Draw moon shape to stencil buffer
		gl.glStencilFunc(GL2.GL_ALWAYS, 1, 1);
		gl.glStencilOp(GL2.GL_REPLACE, GL2.GL_REPLACE, GL2.GL_REPLACE);
		gl.glColorMask(false, false, false, false);
		drawFilledCircle(gl, canvasWidth - 150, canvasHeight - 150, 100); // Adjust coordinates and radius as needed

		// Draw shadow using stencil buffer
		gl.glStencilFunc(GL2.GL_EQUAL, 0, 1);
		gl.glStencilOp(GL2.GL_KEEP, GL2.GL_KEEP, GL2.GL_KEEP);
		gl.glColorMask(true, true, true, true);
		gl.glColor4f(0.2f, 0.2f, 0.2f, 0.5f); // Shadow color
		drawFilledCircle(gl, canvasWidth - 130 - shadowXOffset, canvasHeight - 170, 100); // Adjust coordinates as needed for shadow offset

		// Disable stencil test
		gl.glDisable(GL2.GL_STENCIL_TEST);

		// Draw moon shape
		gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f); // Moon color
		drawFilledCircle(gl, canvasWidth - 150, canvasHeight - 150, 100); // Adjust coordinates and radius as needed
	}


	private void drawFilledCircle(GL2 gl, float centerX, float centerY, float radius) {
		int numSegments = 50; // Number of segments to approximate a circle
		gl.glBegin(GL2.GL_TRIANGLE_FAN);
		gl.glVertex2f(centerX, centerY); // Center of the circle
		for (int i = 0; i <= numSegments; i++) {
			double angle = 2.0 * Math.PI * i / numSegments;
			float x = (float) (centerX + radius * Math.cos(angle));
			float y = (float) (centerY + radius * Math.sin(angle));
			gl.glVertex2f(x, y);
		}
		gl.glEnd();
	}



}



//******************************************************************************
