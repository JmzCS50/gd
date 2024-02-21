//******************************************************************************
// Copyright (C) 2016-2021 University of Oklahoma Board of Trustees.
//******************************************************************************
// Last modified: Fri Mar  5 19:03:36 2021 by Chris Weaver
//******************************************************************************
// Major Modification History:
//
// 20160209 [weaver]:	Original file.
// 20190129 [weaver]:	Updated to JOGL 2.3.2 and cleaned up.
// 20190203 [weaver]:	Additional cleanup and more extensive comments.
// 20200121 [weaver]:	Modified to set up OpenGL and UI on the Swing thread.
// 20201215 [weaver]:	Added setIdentifyPixelScale() to canvas setup.
// 20210209 [weaver]:	Added point smoothing for Hi-DPI displays.
//
//******************************************************************************
// Notes:
//
// Warning! This code uses deprecated features of OpenGL, including immediate
// mode vertex attribute specification, for sake of easier classroom learning.
// See www.khronos.org/opengl/wiki/Legacy_OpenGL
//
//******************************************************************************

package edu.ou.cs.cg.assignment.solution01;

//import java.lang.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Random;
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

	private static final String		DEFAULT_NAME = "Solution01";
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
	private int				w;				// Canvas width
	private int				h;				// Canvas height
	private int				k = 0;			// Animation counter
	private TextRenderer		renderer;

	private int				m = 1;			// Number of points to draw
	private int				mode = 0;		// Mode in [0,3]
	private boolean			p1 = false;		// Draw lines?
	private boolean			p2 = false;		// Use alternative coordinates?

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
	public void	init(GLAutoDrawable drawable)
	{
		w = drawable.getSurfaceWidth();
		h = drawable.getSurfaceHeight();

		renderer = new TextRenderer(new Font("Serif", Font.PLAIN, 18),
									true, true);

		initPipeline(drawable);
	}

	// Notification to release resources for the GLContext.
	public void	dispose(GLAutoDrawable drawable)
	{
		renderer = null;
	}

	// Called to initiate rendering of each frame into the GLCanvas.
	public void	display(GLAutoDrawable drawable)
	{
		update(drawable);
		render(drawable);
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

		if (m > 100000)						// Check point cap
		{
			m = 1;								// Reset point count
			mode = ((mode == 3) ? 0 : (mode + 1));	// Advance mode (cyclically)
			p1 = ((mode == 1) || (mode == 2));		// Set point/line mode
			p2 = ((mode == 2) || (mode == 3));	// Set coordinates mode
		}
		else
		{
			m++;								// Faster increase at low counts
		}

		m = (int)Math.floor(m * 1.02) + 1;		// Increase point count
	}

	// Render the scene model and display the current animation frame.
	private void	render(GLAutoDrawable drawable)
	{
		GL2	gl = drawable.getGL().getGL2();

		gl.glClear(GL.GL_COLOR_BUFFER_BIT);	// Clear the buffer

		//setProjection(gl);					// Use a coordinate system

		// Draw the scene
		drawTinkerbellMap(gl);					// Draw the Tinkerbell map
		drawText(drawable);					// Draw some text

		gl.glFlush();							// Finish and display
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

	// Position and orient the default camera to view in 2-D, centered above.
	private void	setProjection(GL2 gl)
	{
		GLU	glu = GLU.createGLU();

		gl.glMatrixMode(GL2.GL_PROJECTION);		// Prepare for matrix xform
		gl.glLoadIdentity();						// Set to identity matrix
		glu.gluOrtho2D(-1.0f, 1.0f, -1.0f, 1.0f);	// 2D translate and scale
	}

	//**********************************************************************
	// Private Methods (Scene)
	//**********************************************************************

	// This page is helpful:
	// en.wikipedia.org/wiki/Tinkerbell_map
	private void	drawTinkerbellMap(GL2 gl)
	{
		if (p1)									// Line or point mode?
			gl.glBegin(GL.GL_LINE_STRIP);		// Specify line segment sequence
		else
			gl.glBegin(GL.GL_POINTS);			// Specify point set

		gl.glColor3f(1.0f, 1.0f, 1.0f);		// Draw in white

		double		a = 0.9;					// Constant for updating x
		double		b = -0.6013;				// Constant for updating x
		double		c = 2.0;					// Constant for updating y
		double		d = 0.50;					// Constant for updating y
		double		lx = -0.72;				// Initial x coordinate
		double		ly = -0.64;				// Initial y coordinate

		for (int i=0; i<m; i++)
		{
			double	yprev = ly;			// Remember y_previous before updating
			double	llx = lx * lx - ly * ly + a * lx + b * ly;
			double	lly = 2 * lx * ly + c * lx + d * ly;

			lx = llx;
			ly = lly;
			//System.out.println(" " + lx + " " + ly);

			// Make the map fit into the scene by translating the x coordinate,
			// and translating and scaling the y coordinate. (Changing the x
			// and y ranges in setProjection() above might be a better way.)
			if (p2)								// Draw (y_previous, y_current)
				gl.glVertex2d((yprev + 0.5) / 1.1, (ly + 0.5) / 1.1);
			else								// Draw (x_current, y_current)
				gl.glVertex2d(lx + 0.4, (ly + 0.5) / 1.1);
}

		gl.glEnd();
	}

	// Warning! Text is drawn in unprojected canvas/viewport coordinates.
	// For more on text rendering, the example on this page is long but helpful:
	// jogamp.org/jogl-demos/src/demos/j2d/FlyingText.java
	private void	drawText(GLAutoDrawable drawable)
	{
		renderer.beginRendering(w, h);
		renderer.setColor(0.75f, 0.75f, 0.75f, 1.0f);
		renderer.draw("Tinkerbell map (Points: " + m + ")", 2, h - 14);
		renderer.endRendering();
	}
}

//******************************************************************************
