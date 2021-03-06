/*
 * File: CyFrame.java
 * Google Summer of Code
 * Written by Steve Federowicz with help from Scooter Morris
 * 
 * The CyFrame class is essentially a wrapper on a CyNetworkView. It works by having a 
 * populate() method which essentially extracts the necessary view data from the current 
 * CyNetworkView and stores it in the CyFrame. Each CyFrame also contains a display() 
 * method which updates the current network view based upon the visual data stored in 
 * that particular CyFrame. It also can hold an image of the network and contains a facility 
 * for writing this image to a file. 
 * 
 */


package edu.ucsf.rbvi.CyAnimator.internal.model;   

import java.util.*;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.*;
import java.awt.Paint;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.NetworkViewTaskFactory;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.annotations.Annotation;
import org.cytoscape.view.presentation.annotations.AnnotationFactory;
import org.cytoscape.view.presentation.annotations.AnnotationManager;
import org.cytoscape.view.presentation.annotations.ArrowAnnotation;
import org.cytoscape.view.presentation.annotations.BoundedTextAnnotation;
import org.cytoscape.view.presentation.annotations.ImageAnnotation;
import org.cytoscape.view.presentation.annotations.ShapeAnnotation;
import org.cytoscape.view.presentation.annotations.TextAnnotation;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.values.ArrowShape;
import org.cytoscape.view.presentation.property.values.LineType;
import org.cytoscape.view.presentation.property.values.NodeShape;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskObserver;
import org.cytoscape.work.TunableSetter;
import org.cytoscape.work.util.BoundedDouble;
import org.cytoscape.work.util.ListSingleSelection;

public class CyFrame {

	private String frameid = "";
	private static final String PNG = "png";
	private HashMap<Long, NodeShape> nodeShapeMap;
	private HashMap<Long, double[]> nodePosMap;
	private HashMap<Long, Color> nodeColMap;
	private HashMap<Long, Integer> nodeOpacityMap;
	private HashMap<Long, Color> nodeFillColMap;
	private HashMap<Long, Integer> nodeFillOpacityMap;
	private HashMap<Long, Double> nodeBorderWidthMap;
	private HashMap<Long, Color> nodeBorderColorMap;
	private HashMap<Long, Integer> nodeBorderTransMap;
	private HashMap<Long, double[]> nodeSizeMap;
	private HashMap<Long, String> nodeLabelMap;
	private HashMap<Long, Color> nodeLabelColMap;
	private HashMap<Long, Integer> nodeLabelFontSizeMap;
	private HashMap<Long, Integer> nodeLabelTransMap;
	private HashMap<Long, Font> nodeLabelFontMap;
	private HashMap<Long, Double> nodeLabelWidthMap;
	private HashMap<CyNode, CyNode> record;
	private HashMap<CyEdge, CyEdge> recordEdge;

	private HashMap<Long, Integer> edgeOpacityMap;
	private HashMap<Long, Integer> edgeStrokeOpacityMap;
	private HashMap<Long, Color> edgeColMap;
	private HashMap<Long, Color> edgeStrokeColMap;
	private HashMap<Long, Double> edgeWidthMap;
	private HashMap<Long, String> edgeLabel;
	private HashMap<Long, Color> edgeLabelColMap;
	private HashMap<Long, Integer> edgeLabelFontSizeMap;
	private HashMap<Long, Integer> edgeLabelTransMap;
	private HashMap<Long, Font> edgeLabelFontMap;
	private HashMap<Long, ArrowShape> edgeSourceArrowShapeMap;
	private HashMap<Long, ArrowShape> edgeTargetArrowShapeMap;
	private HashMap<Long, LineType> edgeLineTypeMap;

	private String title = null;
	private Paint backgroundPaint = null;
	private double zoom = 0;
	private double size = 0;
	private double width = 0;
	private double height = 0;

	// private List<Annotation> currAnnotationList;
	private List<Annotation> annotationList;
	private HashMap<Integer, Point> annotationPosMap;
	private HashMap<Integer, Color> annotationFillColorMap;
	private HashMap<Integer, Color> annotationBorderColorMap;
	private HashMap<Integer, Color> annotationTextColorMap;
	private HashMap<Integer, Double> annotationZoomMap;
	private HashMap<Integer, Double> annotationFontSizeMap;
	private HashMap<Integer, Double> annotationBorderWidthMap;
	private HashMap<Integer, String> annotationTextMap;
	private HashMap<Integer, String> annotationShapeMap;
	private HashMap<Integer, Dimension> annotationSizeMap;

	private double xalign;
	private double yalign;

	private CyServiceRegistrar bundleContext;
	private CyApplicationManager appManager;
	private AnnotationManager annotationManager;
	private final AnnotationFactory annotationFactory;
	private CyNetworkView networkView = null;
	private CyNetwork currentNetwork = null;
	private CyTable nodeTable = null, edgeTable = null;
	private BufferedImage networkImage = null;
	private Map<Long, View<CyNode>> nodeMap = null;
	private Map<Long, View<CyEdge>> edgeMap = null;
	private VisualStyle vizStyle = null;
	private List<CyNode> nodeList = null;
	private List<CyEdge> edgeList = null;
	private List<Long> nodeIdList = null;
	private List<Long> edgeIdList = null;
	private List<Long> annotationIdList = null;
	private int intercount = 0;
	private Point3D centerPoint = null;
	private SynchronousTaskManager<?> taskManager;

	private static final int IMAGE_WIDTH = 200, IMAGE_HEIGHT = 150;
//	private DGraphView dview = null; 

	/**
	 * Creates this CyFrame by initializing and populating all of the fields.
	 * 
	 * @param currentNetwork
	 */
	public CyFrame(CyServiceRegistrar bc){
		bundleContext = bc;
		appManager = bundleContext.getService(CyApplicationManager.class);
		annotationManager = bundleContext.getService(AnnotationManager.class);
		annotationFactory = bundleContext.getService(AnnotationFactory.class);
		taskManager = bundleContext.getService(SynchronousTaskManager.class);
		nodeShapeMap = new HashMap<Long, NodeShape>();
		nodePosMap = new HashMap<Long, double[]>();
		nodeColMap = new HashMap<Long, Color>();
		nodeFillColMap = new HashMap<Long, Color>();
		nodeLabelMap = new HashMap<Long, String>();
		nodeLabelColMap = new HashMap<Long, Color>();
		nodeLabelFontSizeMap = new HashMap<Long, Integer>();
		nodeLabelTransMap = new HashMap<Long, Integer>();
		nodeLabelFontMap = new HashMap<Long, Font>();
		nodeLabelWidthMap = new HashMap<Long, Double>();
		nodeSizeMap = new HashMap<Long, double[]>();
		nodeBorderWidthMap = new HashMap<Long, Double>();
		nodeBorderColorMap = new HashMap<Long, Color>();
		nodeBorderTransMap = new HashMap<Long, Integer>();
		record = new HashMap<CyNode, CyNode>();
		recordEdge = new HashMap<CyEdge, CyEdge>();
		edgeMap = new HashMap<Long, View<CyEdge>>();
		nodeMap = new HashMap<Long, View<CyNode>>();
		nodeOpacityMap = new HashMap<Long, Integer>();
		nodeFillOpacityMap = new HashMap<Long, Integer>();
		edgeOpacityMap = new HashMap<Long, Integer>();
		edgeStrokeOpacityMap = new HashMap<Long, Integer>();
		edgeColMap = new HashMap<Long, Color>();
		edgeStrokeColMap = new HashMap<Long, Color>();
		edgeLabel = new HashMap<Long, String>();
		edgeLabelColMap = new HashMap<Long, Color>();
		edgeLabelFontSizeMap = new HashMap<Long, Integer>();
		edgeLabelTransMap = new HashMap<Long, Integer>();
		edgeLabelFontMap = new HashMap<Long, Font>();
		edgeWidthMap = new HashMap<Long, Double>();
		edgeSourceArrowShapeMap = new HashMap<Long, ArrowShape>();
		edgeTargetArrowShapeMap = new HashMap<Long, ArrowShape>();
		edgeLineTypeMap = new HashMap<Long, LineType>();
		annotationList = new ArrayList<Annotation>();
		annotationPosMap = new HashMap<Integer, Point>();
		annotationSizeMap = new HashMap<Integer, Dimension>();
		annotationZoomMap = new HashMap<Integer, Double>();
		annotationFillColorMap = new HashMap<Integer, Color>();
		annotationBorderColorMap = new HashMap<Integer, Color>();
		annotationTextColorMap = new HashMap<Integer, Color>();
		annotationFontSizeMap = new HashMap<Integer, Double>();
		annotationBorderWidthMap = new HashMap<Integer, Double>();
		annotationTextMap = new HashMap<Integer, String>();
		annotationShapeMap = new HashMap<Integer, String>();
		this.currentNetwork = appManager.getCurrentNetwork();
		networkView = appManager.getCurrentNetworkView();
		nodeTable = currentNetwork.getDefaultNodeTable();
		edgeTable = currentNetwork.getDefaultEdgeTable();
	//	this.dview = (DGraphView)networkView;
		this.centerPoint = new Point3D(networkView.getVisualProperty(BasicVisualLexicon.NETWORK_CENTER_X_LOCATION),
									networkView.getVisualProperty(BasicVisualLexicon.NETWORK_CENTER_Y_LOCATION),
									networkView.getVisualProperty(BasicVisualLexicon.NETWORK_CENTER_Z_LOCATION));

		nodeIdList = new ArrayList<Long>();
		edgeIdList = new ArrayList<Long>();
		annotationIdList = new ArrayList<Long>();
	
		// Initialize our node view maps
		for (View<CyEdge> ev: networkView.getEdgeViews()) {
			if (ev.getModel() == null) continue;
			long edgeid = ev.getModel().getSUID();//edgeTable.getRow(ev.getModel().getSUID()).get(CyNetwork.NAME, String.class);
			edgeMap.put(edgeid, ev);
			edgeIdList.add(edgeid);
		}

		// Initialize our edge view maps
		for (View<CyNode> nv: networkView.getNodeViews()) {
			long nodeid = nv.getModel().getSUID();//nodeTable.getRow(nv.getModel().getSUID()).get(CyNetwork.NAME, String.class);
			nodeMap.put(nodeid, nv);
			nodeIdList.add(nodeid);
		}
	
		// Initialize our annotations list
		List<Annotation> currAnnotationList = annotationManager.getAnnotations(networkView);
		if(currAnnotationList != null){
			for (Annotation ann: currAnnotationList){
				if (ann instanceof ArrowAnnotation)
					continue;
				annotationList.add(ann);
				annotationIdList.add((long) ann.hashCode());
				// System.out.println("Added annotation: "+ann.toString()+" with ID: "+(long) ann.hashCode());
			}
			// Make sure Arrow annotations are after everything else
			for (Annotation ann: currAnnotationList){
				if (ann instanceof ArrowAnnotation) {
					annotationList.add(ann);
					annotationIdList.add((long) ann.hashCode());
				// System.out.println("Added annotation: "+ann.toString()+" with ID: "+(long) ann.hashCode());
				}
			}
		}

		// Remember the visual style
		VisualMappingManager visualManager = bundleContext.getService(VisualMappingManager.class);
		vizStyle = visualManager.getCurrentVisualStyle();

		// Get our initial nodeList
		nodeList = currentNetwork.getNodeList();

		// Get our initial edgeList
		edgeList = currentNetwork.getEdgeList();
	}

	/*
	 * Captures all of the current visual settings for nodes and edges from a 
	 * CyNetworkView and stores them in this frame.
	 */
	public void populate() {
		title = networkView.getVisualProperty(BasicVisualLexicon.NETWORK_TITLE);
		backgroundPaint = networkView.getVisualProperty(BasicVisualLexicon.NETWORK_BACKGROUND_PAINT);
		zoom = networkView.getVisualProperty(BasicVisualLexicon.NETWORK_SCALE_FACTOR);
		size = networkView.getVisualProperty(BasicVisualLexicon.NETWORK_SIZE);
		width = networkView.getVisualProperty(BasicVisualLexicon.NETWORK_WIDTH);
		height = networkView.getVisualProperty(BasicVisualLexicon.NETWORK_HEIGHT);
		xalign = networkView.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
		yalign = networkView.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);
	
	//	dview = (DGraphView)networkView;
	
		CyTable nodeTable = networkView.getModel().getDefaultNodeTable();
		for(CyNode node: nodeList){
	
			View<CyNode> nodeView = networkView.getNodeView(node);
			if(nodeView == null){ continue; }
			long nodeName = node.getSUID();//nodeTable.getRow(node.getSUID()).get(CyNetwork.NAME, String.class);

			// stores node shape type
			NodeShape shape = nodeView.getVisualProperty(BasicVisualLexicon.NODE_SHAPE);
			nodeShapeMap.put(nodeName, shape);

			//stores the x and y position of the node
			double[] xy = new double[3];
			xy[0] = nodeView.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
			xy[1] = nodeView.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);
			xy[2] = nodeView.getVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION);
			nodePosMap.put(nodeName, xy);
		
			double height = nodeView.getVisualProperty(BasicVisualLexicon.NODE_HEIGHT);
			double width = nodeView.getVisualProperty(BasicVisualLexicon.NODE_WIDTH);
			double[] size = {height, width};
			nodeSizeMap.put(nodeName, size);
		
			double borderWidth = nodeView.getVisualProperty(BasicVisualLexicon.NODE_BORDER_WIDTH);
			nodeBorderWidthMap.put(nodeName, borderWidth);
			Color borderColor = (Color) nodeView.getVisualProperty(BasicVisualLexicon.NODE_BORDER_PAINT);
			nodeBorderColorMap.put(nodeName, borderColor);
			Integer borderTrans = nodeView.getVisualProperty(BasicVisualLexicon.NODE_BORDER_TRANSPARENCY);
			nodeBorderTransMap.put(nodeName, borderTrans);
		
			//grab color and opacity
			Color nodeColor = (Color)nodeView.getVisualProperty(BasicVisualLexicon.NODE_PAINT);
			Integer trans = nodeColor.getAlpha();
			//store in respective hashmap
			nodeColMap.put(nodeName, nodeColor);
			nodeOpacityMap.put(nodeName, trans);

			//grab color and opacity
			Color nodeFillColor = (Color)nodeView.getVisualProperty(BasicVisualLexicon.NODE_FILL_COLOR);
			Integer transFill = nodeView.getVisualProperty(BasicVisualLexicon.NODE_TRANSPARENCY);
			//store in respective hashmap
			nodeFillColMap.put(nodeName, nodeFillColor);
			nodeFillOpacityMap.put(nodeName, transFill);

			// Grab the label information
			String label = nodeView.getVisualProperty(BasicVisualLexicon.NODE_LABEL);
			nodeLabelMap.put(nodeName, label);
			Color labelColor = (Color)nodeView.getVisualProperty(BasicVisualLexicon.NODE_LABEL_COLOR);
			nodeLabelColMap.put(nodeName, labelColor);
			Integer labelFontSize = nodeView.getVisualProperty(BasicVisualLexicon.NODE_LABEL_FONT_SIZE);
			nodeLabelFontSizeMap.put(nodeName, labelFontSize);
			Integer labelTrans = nodeView.getVisualProperty(BasicVisualLexicon.NODE_LABEL_TRANSPARENCY);
			nodeLabelTransMap.put(nodeName, labelTrans);
			Font font = nodeView.getVisualProperty(BasicVisualLexicon.NODE_LABEL_FONT_FACE);
			nodeLabelFontMap.put(nodeName, font);
			Double labelWidth = nodeView.getVisualProperty(BasicVisualLexicon.NODE_LABEL_WIDTH);
			nodeLabelWidthMap.put(nodeName, labelWidth);

			centerPoint = new Point3D(networkView.getVisualProperty(BasicVisualLexicon.NETWORK_CENTER_X_LOCATION),
			networkView.getVisualProperty(BasicVisualLexicon.NETWORK_CENTER_Y_LOCATION),
			networkView.getVisualProperty(BasicVisualLexicon.NETWORK_CENTER_Z_LOCATION));
		
		}

		CyTable edgeTable = networkView.getModel().getDefaultEdgeTable();
		for(CyEdge edge: edgeList){
		
			View<CyEdge> edgeView = networkView.getEdgeView(edge);
			if(edgeView == null){  continue; }
			long edgeName = edge.getSUID();//edgeTable.getRow(edge.getSUID()).get(CyNetwork.NAME, String.class);

			//grab color and opacity
			Color p = (Color)edgeView.getVisualProperty(BasicVisualLexicon.EDGE_PAINT);
			Integer trans = p.getAlpha();
			//store in respective hashmap
			edgeColMap.put(edgeName, p);
			edgeOpacityMap.put(edgeName, trans);
			double edgeWidth = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_WIDTH);
			edgeWidthMap.put(edgeName, edgeWidth);

			//grab color and opacity
			Color pStroke = (Color)edgeView.getVisualProperty(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT);
			Integer transStroke = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_TRANSPARENCY);
			//store in respective hashmap
			edgeStrokeColMap.put(edgeName, pStroke);
			edgeStrokeOpacityMap.put(edgeName, transStroke);

			// Grab the label information
			String label = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_LABEL);
			edgeLabel.put(edgeName, label);
			Color labelColor = (Color)edgeView.getVisualProperty(BasicVisualLexicon.EDGE_LABEL_COLOR);
			edgeLabelColMap.put(edgeName, labelColor);
			Integer labelFontSize = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_LABEL_FONT_SIZE);
			Integer labelTransMap = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_LABEL_TRANSPARENCY);
			edgeLabelFontSizeMap.put(edgeName, labelFontSize);
			edgeLabelTransMap.put(edgeName, labelTransMap);
			Font font = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_LABEL_FONT_FACE);
			edgeLabelFontMap.put(edgeName, font);

			// Grab the shape information
			ArrowShape source = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_SOURCE_ARROW_SHAPE);
			edgeSourceArrowShapeMap.put(edgeName, source);
			ArrowShape target = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE);
			edgeTargetArrowShapeMap.put(edgeName, target);
			LineType line = edgeView.getVisualProperty(BasicVisualLexicon.EDGE_LINE_TYPE);
			edgeLineTypeMap.put(edgeName, line);
		}

		// Handle the annotations.  Note that we need to be really careful
		// about the order since BoundedTextAnnotation (for example) implements
		// both ShapeAnnotation and TextAnnotation if either of those are first,
		// we won't get the right information.
		for(Annotation ann: annotationList){
			Double x = new Double(ann.getArgMap().get(Annotation.X));
			Double y = new Double(ann.getArgMap().get(Annotation.Y));
			// System.out.println("ArgMap for annotation "+ann+"="+ann.getArgMap());
			// System.out.println("Position for annotation "+ann+"("+ann.hashCode()+") "+"="+x.intValue()+","+y.intValue());
			annotationPosMap.put( ann.hashCode(), new Point( x.intValue(), y.intValue() ) );
			annotationZoomMap.put(ann.hashCode(), ann.getSpecificZoom());
			if( ann instanceof BoundedTextAnnotation){
				BoundedTextAnnotation bta = (BoundedTextAnnotation) ann;
				annotationFillColorMap.put(bta.hashCode(), (Color) bta.getFillColor());
				annotationBorderColorMap.put(bta.hashCode(), (Color) bta.getBorderColor());
				annotationTextColorMap.put(bta.hashCode(), bta.getTextColor());
				annotationFontSizeMap.put(bta.hashCode(), bta.getFontSize());
				annotationBorderWidthMap.put(bta.hashCode(), bta.getBorderWidth());
				annotationTextMap.put(bta.hashCode(), bta.getText());
				annotationShapeMap.put(bta.hashCode(), bta.getShapeType());
				setAnnotationSize(bta);
			} else if(ann instanceof TextAnnotation){
				TextAnnotation ta = (TextAnnotation) ann;
				annotationTextColorMap.put(ta.hashCode(), ta.getTextColor());
				annotationFontSizeMap.put(ta.hashCode(), ta.getFontSize());
				annotationTextMap.put(ta.hashCode(), ta.getText());
			}else if( ann instanceof ImageAnnotation){
				ImageAnnotation ia = (ImageAnnotation) ann;
				annotationBorderColorMap.put(ia.hashCode(), (Color) ia.getBorderColor());
				annotationBorderWidthMap.put(ia.hashCode(), ia.getBorderWidth());
				annotationShapeMap.put( ia.hashCode(), ia.getShapeType());
				setAnnotationSize(ia);
			}else if( ann instanceof ShapeAnnotation){
				ShapeAnnotation sa = (ShapeAnnotation) ann;
				annotationFillColorMap.put(sa.hashCode(), (Color) sa.getFillColor());
				annotationBorderColorMap.put(sa.hashCode(), (Color) sa.getBorderColor());
				annotationBorderWidthMap.put(sa.hashCode(), sa.getBorderWidth());
				annotationShapeMap.put( sa.hashCode(), sa.getShapeType());
				setAnnotationSize(sa);
			}else if( ann instanceof ArrowAnnotation){
				ArrowAnnotation aa = (ArrowAnnotation) ann;
				annotationBorderColorMap.put(aa.hashCode(), (Color) aa.getLineColor());
				annotationFillColorMap.put(aa.hashCode(), (Color) aa.getArrowColor(ArrowAnnotation.ArrowEnd.TARGET));
				annotationTextColorMap.put(aa.hashCode(), (Color) aa.getArrowColor(ArrowAnnotation.ArrowEnd.SOURCE));
			}
		}

	}

	/**
	 * Captures and stores a thumbnail image from the current CyNetworkView for
	 * this frame.
	 * @throws IOException throws exception if cannot create or read temporary file
	 */
	public void captureImage() throws IOException {
	
	/*	double scale = .35;
		double wscale = .25; */

		CyNetworkView view = appManager.getCurrentNetworkView();
	
		NetworkViewTaskFactory exportImageTaskFactory = bundleContext.getService(NetworkViewTaskFactory.class, "(&(commandNamespace=view)(command=export))");
		if (exportImageTaskFactory != null && exportImageTaskFactory.isReady(view)) {
			TunableSetter tunableSetter = bundleContext.getService(TunableSetter.class);
			Map<String, Object> tunables = new HashMap<String, Object>();
			List<String> fileTypeList = new ArrayList<String>();
			fileTypeList.add(PNG);
			ListSingleSelection<String> fileType = new ListSingleSelection<String>(fileTypeList);
			fileType.setSelectedValue(PNG);
			tunables.put("options", fileType);
			final File temporaryImageFile = File.createTempFile("temporaryCytoscapeImage", ".png");
			tunables.put("OutputFile", temporaryImageFile);
			taskManager.execute(tunableSetter.createTaskIterator(
					exportImageTaskFactory.createTaskIterator(view), tunables),
					new TaskObserver() {
					
						public void taskFinished(ObservableTask arg0) {
							// TODO Auto-generated method stub
						
						}
					
						public void allFinished(FinishStatus arg0) {
							BufferedImage image = null, scaledImage = null;
							try {
								image = ImageIO.read(temporaryImageFile);
								int type = image.getType() == 0? BufferedImage.TYPE_INT_ARGB : image.getType();
								scaledImage = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, type);
								Graphics2D g = scaledImage.createGraphics();
								g.drawImage(image, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null);
								g.dispose();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							networkImage = scaledImage;
						//	temporaryImageFile.delete();
						}
					});
		}

	}

	/*
	 * Cycles through the list of nodes and edges and updates the node and edge views 
	 * based upon the visual data stored as part of the CyFrame.  
	 */
	public void display() {
		VisualMappingManager visualManager = (VisualMappingManager) bundleContext.getService(VisualMappingManager.class);
		visualManager.setVisualStyle(vizStyle, networkView);

		// We want to use the current view in case we're interpolating
		// across views
		final CyNetworkView currentView = appManager.getCurrentNetworkView();

		// Make sure everything is on the EDT
		try {
		SwingUtilities.invokeAndWait( new Runnable () {
			public void run() {
				handleMissingEdges(currentView);
				handleMissingNodes(currentView);

				handleNodes(currentView);
				handleEdges(currentView);

				handleNetwork(currentView);
		
				handleMissingAnnotations(currentView);
				handleAnnotations(currentView);

				currentView.updateView();
			}
		} );
		} catch (Exception e) {}
	}

  /**
	 * Removes temporarily added nodes and edges from network.
	 *
	 *
	 */
   public void clearDisplay(){
	 final Collection<CyEdge> removeAddedEdges = new ArrayList<CyEdge>();
	 final Collection<Long> removeAddedEdgesKeys = new ArrayList<Long>();
	 for (CyEdge e: recordEdge.values() ){
	   removeAddedEdges.add(e);
	   removeAddedEdgesKeys.add(e.getSUID());
	 }

	 final Collection<CyNode> removeAddedNodes = new ArrayList<CyNode>();
	 final Collection<Long> removeAddedKeys = new ArrayList<Long>();
	 for (CyNode n: record.values() ){
	   removeAddedNodes.add(n);
	   removeAddedKeys.add(n.getSUID());
	 }

		try {
		SwingUtilities.invokeAndWait( new Runnable () {
			public void run() {
	 			CyNetwork network = appManager.getCurrentNetworkView().getModel();
				network.removeEdges(removeAddedEdges);
	 			network.getDefaultEdgeTable().deleteRows(removeAddedEdgesKeys);

	 			network.removeNodes(removeAddedNodes);
	 			network.getDefaultNodeTable().deleteRows(removeAddedKeys);
	 			appManager.getCurrentNetworkView().updateView();
			}
		} );
		} catch(Exception e) {}
  }

	private void handleMissingNodes(final CyNetworkView currentView) {
		// Initialize our edge view maps
		List<CyNode> removeNodes = new ArrayList<CyNode>();
		CyTable curNodeTable = currentView.getModel().getDefaultNodeTable();
		for (CyNode nv : currentView.getModel().getNodeList()) {
			if (!nodeMap.containsKey(nv.getSUID()/*curNodeTable.getRow(nv.getSUID()).get(CyNetwork.NAME, String.class)*/)) {
			removeNodes.add(nv);
			}
		}

		for (CyNode node : removeNodes) {
			View<CyNode> nodeView = currentView.getNodeView(node);
			if (nodeView == null) {
				continue;
			}
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_VISIBLE)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_VISIBLE);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_VISIBLE, false);
			currentView.updateView();
		}
	}

	private void handleMissingEdges(final CyNetworkView currentView) {
		// First see if we have any views we need to remove
		Collection<CyEdge> removeEdges = new ArrayList<CyEdge>();
		CyTable curEdgeTable = currentView.getModel().getDefaultEdgeTable();
		for (CyEdge ev: currentView.getModel().getEdgeList()) {
			if (!edgeMap.containsKey(ev.getSUID()/*curEdgeTable.getRow(ev.getSUID()).get(CyNetwork.NAME, String.class)*/))
				removeEdges.add(ev);
		}

		for (CyEdge edge : removeEdges) {
			View<CyEdge> edgeView = currentView.getEdgeView(edge);
			if (edgeView == null) {
				continue;
			}
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_VISIBLE)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_VISIBLE);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_VISIBLE, false);
			currentView.updateView();
		}
	}

	private void handleMissingAnnotations(final CyNetworkView currentView) {
		final List<Annotation> currAnnotationList = annotationManager.getAnnotations(networkView);

		// The annotation sets might not be on the EDT (Cytoscape core bug).  We need
		// to make sure we are
		
		// hide annotation which were not present earlier
		if( currAnnotationList != null){
			for (Annotation ann : currAnnotationList) {
				if (!annotationList.contains(ann)) {
					// make ann invisible here
					Color transparent = new Color(0,0,0,0);
					if (ann instanceof ImageAnnotation) {
						ImageAnnotation ia = (ImageAnnotation) ann;
						ia.setBorderColor(transparent);
					} else if (ann instanceof BoundedTextAnnotation) {
						BoundedTextAnnotation bta = (BoundedTextAnnotation) ann;
						bta.setFillColor(transparent);
						bta.setBorderColor(transparent);
						bta.setTextColor(transparent);
					} else if (ann instanceof ShapeAnnotation) {
						ShapeAnnotation sa = (ShapeAnnotation) ann;
						sa.setFillColor(transparent);
						sa.setBorderColor(transparent);
					} else if (ann instanceof TextAnnotation) {
						TextAnnotation ta = (TextAnnotation) ann;
						ta.setTextColor(transparent);
					} else if (ann instanceof ArrowAnnotation) {
						ArrowAnnotation aa = (ArrowAnnotation) ann;
						aa.setLineColor(transparent);
						aa.setArrowColor(ArrowAnnotation.ArrowEnd.TARGET, transparent);
						aa.setArrowColor(ArrowAnnotation.ArrowEnd.SOURCE, transparent);
					}
				}else{
					if (ann instanceof ImageAnnotation) {
						ImageAnnotation ia = (ImageAnnotation) ann;
						ia.setBorderColor(annotationBorderColorMap.get(ia.hashCode()));
					} else if (ann instanceof BoundedTextAnnotation) {
						BoundedTextAnnotation bta = (BoundedTextAnnotation) ann;
						bta.setFillColor(annotationFillColorMap.get(bta.hashCode()));
						bta.setBorderColor(annotationBorderColorMap.get(bta.hashCode()));
						bta.setTextColor(annotationTextColorMap.get(bta.hashCode()));
					} else if (ann instanceof ShapeAnnotation) {
						ShapeAnnotation sa = (ShapeAnnotation) ann;
						sa.setFillColor(annotationFillColorMap.get(sa.hashCode()));
						sa.setBorderColor(annotationBorderColorMap.get(sa.hashCode()));
					} else if (ann instanceof TextAnnotation) {
						TextAnnotation ta = (TextAnnotation) ann;
						ta.setTextColor(annotationTextColorMap.get(ta.hashCode()));
					} else if (ann instanceof ArrowAnnotation) {
						ArrowAnnotation aa = (ArrowAnnotation) ann;
						aa.setLineColor(annotationBorderColorMap.get(aa.hashCode()));

						// Special handling for arrow heads.  If we don't have a specific color, use the
						// line color
						if (annotationFillColorMap.get(aa.hashCode()) != null)
							aa.setArrowColor(ArrowAnnotation.ArrowEnd.TARGET, annotationFillColorMap.get(aa.hashCode()));
						else
							aa.setArrowColor(ArrowAnnotation.ArrowEnd.TARGET, annotationBorderColorMap.get(aa.hashCode()));

						if (annotationTextColorMap.get(aa.hashCode()) != null)
							aa.setArrowColor(ArrowAnnotation.ArrowEnd.SOURCE, annotationTextColorMap.get(aa.hashCode()));
						else
							aa.setArrowColor(ArrowAnnotation.ArrowEnd.SOURCE, annotationFillColorMap.get(aa.hashCode()));
					}
				}
			}
		}
	
		// show annotation which were removed later
		if( annotationList != null){
			for (Annotation ann : annotationList) {
				if (!currAnnotationList.contains(ann)) {
					// make ann visible here
					// System.out.println("Adding annotation: "+ann);
					Annotation newAnn = annotationFactory.createAnnotation(ann.getClass(), currentView, ann.getArgMap());
					annotationManager.addAnnotation(newAnn);
				}
			}
		}
	}

	private void handleNodes(final CyNetworkView currentView) {
		for (CyNode node : nodeList) {
			View<CyNode> nodeView = currentView.getNodeView(node);
			if (nodeView == null) {
				// Add temporary node to network for viewing the node which is removed from current network
				CyNode artNode = currentView.getModel().addNode();
				record.put(node, artNode);
				currentView.updateView();
				nodeView = currentView.getNodeView(artNode);
			}
		
			long nodeName = node.getSUID();//curNodeTable.getRow(node.getSUID()).get(CyNetwork.NAME, String.class);
		
			double[] xy = nodePosMap.get(nodeName);
			Color p = nodeColMap.get(nodeName), pFill = nodeFillColMap.get(nodeName);
			Integer trans = nodeOpacityMap.get(nodeName), transFill = nodeFillOpacityMap.get(nodeName);
		
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_VISIBLE)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_VISIBLE);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_VISIBLE, true);
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_SHAPE)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_SHAPE);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_SHAPE, nodeShapeMap.get(nodeName));
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_X_LOCATION)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_X_LOCATION);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_X_LOCATION, xy[0]);
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_Y_LOCATION)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_Y_LOCATION);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION, xy[1]);
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_Z_LOCATION)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_Z_LOCATION);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_Z_LOCATION, xy[2]);
		
			double[] size = nodeSizeMap.get(nodeName);
		
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_HEIGHT)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_HEIGHT);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_HEIGHT, size[0]);
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_WIDTH)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_WIDTH);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_WIDTH, size[1]);
		
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_BORDER_WIDTH)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_BORDER_WIDTH);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_BORDER_WIDTH, nodeBorderWidthMap.get(nodeName));
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_BORDER_PAINT)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_BORDER_PAINT);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_BORDER_PAINT, nodeBorderColorMap.get(nodeName));
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_BORDER_TRANSPARENCY)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_BORDER_TRANSPARENCY);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_BORDER_TRANSPARENCY, nodeBorderTransMap.get(nodeName));
		
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_PAINT)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_PAINT);
			}
			if (p != null) {
				nodeView.setVisualProperty(BasicVisualLexicon.NODE_PAINT, new Color(p.getRed(), p.getGreen(), p.getBlue(), trans));
			}
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_FILL_COLOR)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_FILL_COLOR);
			}
			if (pFill != null) {
				nodeView.setVisualProperty(BasicVisualLexicon.NODE_FILL_COLOR, new Color(pFill.getRed(), 
				                           pFill.getGreen(), pFill.getBlue(), transFill));
			}
		
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_LABEL)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_LABEL);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_LABEL, nodeLabelMap.get(nodeName));
			Color labelColor = nodeLabelColMap.get(nodeName);
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_LABEL_COLOR)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_LABEL_COLOR);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_LABEL_COLOR,
			                           new Color(labelColor.getRed(),
			                           labelColor.getGreen(), labelColor.getBlue()));
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_TRANSPARENCY)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_TRANSPARENCY);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_TRANSPARENCY, nodeFillOpacityMap.get(nodeName));
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_LABEL_FONT_SIZE)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_LABEL_FONT_SIZE);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_LABEL_FONT_SIZE, nodeLabelFontSizeMap.get(nodeName));
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_LABEL_TRANSPARENCY)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_LABEL_TRANSPARENCY);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_LABEL_TRANSPARENCY, nodeLabelTransMap.get(nodeName));
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_LABEL_FONT_FACE)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_LABEL_FONT_FACE);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_LABEL_FONT_FACE, nodeLabelFontMap.get(nodeName));
			if (nodeView.isValueLocked(BasicVisualLexicon.NODE_LABEL_WIDTH)) {
				nodeView.clearValueLock(BasicVisualLexicon.NODE_LABEL_WIDTH);
			}
			nodeView.setVisualProperty(BasicVisualLexicon.NODE_LABEL_WIDTH, nodeLabelWidthMap.get(nodeName));
		}
	}

	private void handleEdges(final CyNetworkView currentView) {
		for(CyEdge edge: getEdgeList()) {

			View<CyEdge> edgeView = currentView.getEdgeView(edge);
			if (edgeView == null) {
				// Add temporary edge to network for viewing the edge which is removed from current network
				CyEdge artEdge = null;
				if (record.containsKey(edge.getSource()) && nodeList.contains(edge.getTarget()) && !record.containsKey(edge.getTarget())) {
					artEdge = currentView.getModel().addEdge(record.get(edge.getSource()), edge.getTarget(), true);
				} else if (nodeList.contains(edge.getSource()) && !record.containsKey(edge.getSource()) && 
									record.containsKey(edge.getTarget())) {
					artEdge = currentView.getModel().addEdge(edge.getSource(), record.get(edge.getTarget()), true);
				} else if (record.containsKey(edge.getSource()) && record.containsKey(edge.getTarget())) {
					artEdge = currentView.getModel().addEdge(record.get(edge.getSource()), record.get(edge.getTarget()), true);
				} else {
					continue;
				}
				currentView.updateView();
				edgeView = currentView.getEdgeView(artEdge);
				recordEdge.put(edge, artEdge);
			}

			long edgeName = edge.getSUID();//curEdgeTable.getRow(edge.getSUID()).get(CyNetwork.NAME, String.class);
			Color p = edgeColMap.get(edgeName), pStroke = edgeStrokeColMap.get(edgeName);
			if (p == null && pStroke == null) {
				continue;
			}
			Integer trans = edgeOpacityMap.get(edgeName), transStroke = edgeStrokeOpacityMap.get(edgeName);

			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_VISIBLE)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_VISIBLE);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_VISIBLE, true);
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_PAINT)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_PAINT);
			}
			if (p != null) {
				edgeView.setVisualProperty(BasicVisualLexicon.EDGE_PAINT, new Color(p.getRed(), p.getGreen(), p.getBlue(), trans));
			}
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT);
			}
			if (pStroke != null) {
				edgeView.setVisualProperty(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT, 
				                           new Color(pStroke.getRed(), pStroke.getGreen(), pStroke.getBlue(), transStroke));
			}
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_WIDTH)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_WIDTH);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_WIDTH, edgeWidthMap.get(edgeName));

			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_LABEL)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_LABEL);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_LABEL, edgeLabel.get(edgeName));
			Color labelColor = edgeLabelColMap.get(edgeName);
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_LABEL_COLOR)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_LABEL_COLOR);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_LABEL_COLOR,
				new Color(labelColor.getRed(),
					labelColor.getGreen(), labelColor.getBlue()));
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_TRANSPARENCY)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_TRANSPARENCY);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_TRANSPARENCY, edgeStrokeOpacityMap.get(edgeName));
			Integer labelFontSize = edgeLabelFontSizeMap.get(edgeName),
				labelTrans = edgeLabelTransMap.get(edgeName);
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_LABEL_FONT_SIZE)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_LABEL_FONT_SIZE);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_LABEL_FONT_SIZE, labelFontSize);
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_LABEL_FONT_FACE)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_LABEL_FONT_FACE);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_LABEL_FONT_FACE, edgeLabelFontMap.get(edgeName));
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_LABEL_TRANSPARENCY)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_LABEL_TRANSPARENCY);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_LABEL_TRANSPARENCY, labelTrans);
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_SOURCE_ARROW_SHAPE)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_SOURCE_ARROW_SHAPE);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_SOURCE_ARROW_SHAPE, edgeSourceArrowShapeMap.get(edgeName));
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE, edgeTargetArrowShapeMap.get(edgeName));
			if (edgeView.isValueLocked(BasicVisualLexicon.EDGE_LINE_TYPE)) {
				edgeView.clearValueLock(BasicVisualLexicon.EDGE_LINE_TYPE);
			}
			edgeView.setVisualProperty(BasicVisualLexicon.EDGE_LINE_TYPE, edgeLineTypeMap.get(edgeName));
		}
	}

	private void handleNetwork(final CyNetworkView currentView) {

		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_TITLE)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_TITLE);
		}
		currentView.setVisualProperty(BasicVisualLexicon.NETWORK_TITLE, title);
		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_BACKGROUND_PAINT)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_BACKGROUND_PAINT);
		}
		currentView.setVisualProperty(BasicVisualLexicon.NETWORK_BACKGROUND_PAINT, backgroundPaint);
		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_SCALE_FACTOR)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_SCALE_FACTOR);
		}
		currentView.setVisualProperty(BasicVisualLexicon.NETWORK_SCALE_FACTOR, zoom);
		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_SIZE)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_SIZE);
		}
		currentView.setVisualProperty(BasicVisualLexicon.NETWORK_SIZE, size);
		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_WIDTH)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_WIDTH);
		}
		currentView.setVisualProperty(BasicVisualLexicon.NETWORK_WIDTH, width);
		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_HEIGHT)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_HEIGHT);
		}
		currentView.setVisualProperty(BasicVisualLexicon.NETWORK_HEIGHT, height);

		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_CENTER_X_LOCATION)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_CENTER_X_LOCATION);
		}
		networkView.setVisualProperty(BasicVisualLexicon.NETWORK_CENTER_X_LOCATION, centerPoint.getX());
		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_CENTER_Y_LOCATION)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_CENTER_Y_LOCATION);
		}
		networkView.setVisualProperty(BasicVisualLexicon.NETWORK_CENTER_Y_LOCATION, centerPoint.getY());
		if (currentView.isValueLocked(BasicVisualLexicon.NETWORK_CENTER_Z_LOCATION)) {
			currentView.clearValueLock(BasicVisualLexicon.NETWORK_CENTER_Z_LOCATION);
		}
		networkView.setVisualProperty(BasicVisualLexicon.NETWORK_CENTER_Z_LOCATION, centerPoint.getZ());
	}

	private void handleAnnotations(final CyNetworkView currentView) {
		for(Annotation ann: annotationList){
			ann.moveAnnotation( annotationPosMap.get( ann.hashCode() ));
			// System.out.println("Setting zoom to: "+annotationZoomMap.get(ann.hashCode()));
			ann.setSpecificZoom( annotationZoomMap.get( ann.hashCode()) );
			// make ann visible here
			if( ann instanceof BoundedTextAnnotation){
				BoundedTextAnnotation bta = (BoundedTextAnnotation) ann;
				bta.setFillColor( annotationFillColorMap.get(bta.hashCode()));
				bta.setBorderColor( annotationBorderColorMap.get(bta.hashCode()));
				bta.setTextColor(annotationTextColorMap.get(bta.hashCode()));
				bta.setFontSize( annotationFontSizeMap.get(bta.hashCode()));
				// System.out.println("Setting bounded text font size to: "+annotationFontSizeMap.get(bta.hashCode()));
				bta.setBorderWidth( annotationBorderWidthMap.get(bta.hashCode()));
				bta.setText( annotationTextMap.get(bta.hashCode()));
				bta.setShapeType( annotationShapeMap.get(bta.hashCode()));
				Dimension d = annotationSizeMap.get(bta.hashCode());
				// System.out.println("Setting bounded text size to: "+d);
				bta.setSize(d.getWidth(), d.getHeight());
			}else if( ann instanceof ImageAnnotation){
				ImageAnnotation ia = (ImageAnnotation) ann;
				ia.setBorderColor( annotationBorderColorMap.get(ia.hashCode()));
				ia.setBorderWidth( annotationBorderWidthMap.get(ia.hashCode()));
				ia.setShapeType( annotationShapeMap.get(ia.hashCode()));
				Dimension d = annotationSizeMap.get(ia.hashCode());
				ia.setSize(d.getWidth(), d.getHeight());
			}else if( ann instanceof ShapeAnnotation){
				ShapeAnnotation sa = (ShapeAnnotation) ann;
				sa.setFillColor( annotationFillColorMap.get(sa.hashCode()));
				sa.setBorderColor( annotationBorderColorMap.get(sa.hashCode()));
				sa.setBorderWidth( annotationBorderWidthMap.get(sa.hashCode()));
				sa.setShapeType( annotationShapeMap.get(sa.hashCode()));
				Dimension d = annotationSizeMap.get(sa.hashCode());
				// System.out.println("Setting shape size to: "+d);
				sa.setSize(d.getWidth(), d.getHeight());
			} else if(ann instanceof TextAnnotation){
				TextAnnotation ta = (TextAnnotation)ann;
				ta.setTextColor( annotationTextColorMap.get(ta.hashCode()));
				// System.out.println("Setting font size to: "+annotationFontSizeMap.get(ta.hashCode()));
				ta.setFontSize( annotationFontSizeMap.get(ta.hashCode()));
				ta.setText( annotationTextMap.get(ta.hashCode()));
			}else if( ann instanceof ArrowAnnotation){
				ArrowAnnotation aa = (ArrowAnnotation) ann;
				aa.setLineColor(annotationBorderColorMap.get(aa.hashCode()));
				aa.setArrowColor(ArrowAnnotation.ArrowEnd.TARGET, annotationFillColorMap.get(aa.hashCode()));
				aa.setArrowColor(ArrowAnnotation.ArrowEnd.SOURCE, annotationTextColorMap.get(aa.hashCode()));
			}
		}
	}

	/**
	 * Return the frame ID for this frame
	 * 
	 * @return the frame ID
	 * 
	 */
	public String getID() {
		return frameid;
	}

	public void setID(String ID) {
		frameid = ID;
	}

	/**
	 * Return the CyNetwork for this frame
	 *
	 * @return the CyNetwork
	 */
	public CyNetwork getCurrentNetwork() {
		return currentNetwork;
	}

	/**
	 * Return the number of frames to be interpolated between this frame and the next.
	 *
	 * @return the frame number
	 */
	public int getInterCount() {
		return intercount;
	}

	/**
	 * Set the number of frames to be interpolated between this frame and the next.
	 *
	 * @param interCount the number of frames to interpret
	 */
	public void setInterCount(int intercount) {
		this.intercount = intercount;
	}

	/**
	 * Return the title of network
	 *
	 * @return title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Set the title of network.
	 *
	 * @param title set the title value
	 */
	public void setTitle(String title) {
		this.title = title;
	}

	/**
	 * Return the zoom value for this frame.
	 *
	 * @return zoom
	 */
	public double getZoom() {
		return zoom;
	}

	/**
	 * Set the zoom value for this frame.
	 *
	 * @param zoom set the zoom value
	 */
	public void setZoom(double zoom) {
		this.zoom = zoom;
	}

	/**
	 * Return the background color value for this frame.
	 *
	 * @return the background color
	 */
	public Paint getBackgroundPaint() {
		return backgroundPaint;
	}

	/**
	 * Set the background color value for this frame.
	 *
	 * @param bg set the background color
	 */
	public void setBackgroundPaint(Paint bg) {
		backgroundPaint = bg;
	}

	/**
	 * @return the network size
	 */
	public Double getNetworkSize() {
		return size;
	}

	/**
	 * Set the network size.
	 *
	 * @param size set the network size
	 */
	public void setNetworkSize(Double size) {
		this.size = size;
	}

	/**
	 * @return the network width
	 */
	public Double getNetworkWidth() {
		return width;
	}

	/**
	 * Set the network width.
	 *
	 * @param width set the network width
	 */
	public void setNetworkWidth(Double width) {
		this.width = width;
	}

	/**
	 * @return the network height
	 */
	public Double getNetworkHeight() {
		return height;
	}

	/**
	 * Set the network height.
	 *
	 * @param height set the network height
	 */
	public void setNetworkHeight(Double height) {
		this.height = height;
	}

	/**
	 * Return the position of annotation.
	 * 
	 * @param hashcode of annotation whose position is to be returned 
	 * @return Point
	 */
	public Point getAnnotationPos(int hashcode) {
		if(annotationPosMap.containsKey(hashcode))
			return annotationPosMap.get(hashcode);
		return null;
	}

	/**
	 * Set the position annotation.
	 * @param hascode of annotation whose position is to be set
	 * @param zoom set the position
	 */
	public void setAnnotationPos(int hashcode, Point pt) {
		// System.out.println("Setting annotation position to: "+pt);
		annotationPosMap.put(hashcode, pt);
	}

	/**
	 * Return the size of the annotation (for those that support it)
	 *
	 * @param hashcode of annotation whose size is to be returned
	 * @return Dimension
	 */
	public Dimension getAnnotationSize(int hashcode) {
		if(annotationSizeMap.containsKey(hashcode))
			return annotationSizeMap.get(hashcode);
		return null;
	}

	/**
	 * Set the size of the annotation (for those that support it)
	 *
	 * @param hashcode of annotation whose size is to be returned
	 * @param size the size of annotation as a Dimension
	 */
	public void setAnnotationSize(int hashcode, Dimension size) {
		annotationSizeMap.put(hashcode, size);
	}

	/**
	 * Return the zoom value for annotation.
	 * 
	 * @param hashcode of annotation whose zoom is to be returned 
	 * @return zoom
	 */
	public double getAnnotationZoom(int hashcode) {
		if(annotationZoomMap.containsKey(hashcode))
		return annotationZoomMap.get(hashcode);
		return 0;
	}

	/**
	 * Set the zoom value for annotation.
	 * @param hascode of annotation whose zoom is to be set
	 * @param zoom set the visibility value
	 */
	public void setAnnotationZoom(int hashcode, double zoom) {
		annotationZoomMap.put(hashcode, zoom);
	}

	/**
	 * Return the Color with which annotation is filled.
	 * 
	 * @param hashcode of annotation who is filled with Color
	 * @return Color
	 */
	public Color getAnnotationFillColor(int hashcode) {
		if(annotationFillColorMap.containsKey(hashcode))
		return annotationFillColorMap.get(hashcode);
		return null;
	}

	/**
	 * Fill the annotation with Color.
	 * @param hascode of annotation who is to be filled with Color
	 * @param color set the fill Color
	 */
	public void setAnnotationFillColor(int hashcode, Color color) {
		annotationFillColorMap.put(hashcode, color);
	}

	/**
	 * Return the Color of border of annotation.
	 * 
	 * @param hashcode of annotation whose border color is to be returned 
	 * @return Color
	 */
	public Color getAnnotationBorderColor(int hashcode) {
		if(annotationBorderColorMap.containsKey(hashcode))
		return annotationBorderColorMap.get(hashcode);
		return null;
	}

	/**
	 * Set the Color of border of annotation.
	 * @param hascode of annotation whose border Color is to be set
	 * @param color set the Color value of border of annotation
	 */
	public void setAnnotationBorderColor(int hashcode, Color color) {
		annotationBorderColorMap.put(hashcode, color);
	}

	/**
	 * Return the Color of text of annotation.
	 * 
	 * @param hashcode of annotation whose text color is to be returned 
	 * @return Color
	 */
	public Color getAnnotationTextColor(int hashcode) {
		if(annotationTextColorMap.containsKey(hashcode))
		return annotationTextColorMap.get(hashcode);
		return null;
	}

	/**
	 * Set the Color of text of annotation.
	 * @param hascode of annotation whose text Color is to be set
	 * @param color set the Color value of text of annotation
	 */
	public void setAnnotationTextColor(int hashcode, Color color) {
		annotationTextColorMap.put(hashcode, color);
	}

	/**
	 * Return the font size of text of annotation.
	 * 
	 * @param hashcode of annotation whose text font size is to be returned 
	 * @return double
	 */
	public double getAnnotationFontSize(int hashcode) {
		if(annotationFontSizeMap.containsKey(hashcode))
		return annotationFontSizeMap.get(hashcode);
		return 0.0;
	}

	/**
	 * Set the font size of text of annotation.
	 * @param hascode of annotation whose text font size is to be set
	 * @param size set the font size value of text of annotation
	 */
	public void setAnnotationFontSize(int hashcode, double size) {
		annotationFontSizeMap.put(hashcode, size);
	}

	/**
	 * Return the border width of annotation.
	 * 
	 * @param hashcode of annotation whose border width is to be returned 
	 * @return double
	 */
	public double getAnnotationBorderWidth(int hashcode) {
		if(annotationBorderWidthMap.containsKey(hashcode))
		return annotationBorderWidthMap.get(hashcode);
		return 0.0;
	}

	/**
	 * Set the border width of annotation.
	 * @param hascode of annotation whose border width is to be set
	 * @param size set the border width of annotation
	 */
	public void setAnnotationBorderWidth(int hashcode, double size) {
		annotationBorderWidthMap.put(hashcode, size);
	}

	/**
	 * Return the text of annotation.
	 * 
	 * @param hashcode of annotation whose text is to be returned 
	 * @return string
	 */
	public String getAnnotationText(int hashcode) {
		if(annotationTextMap.containsKey(hashcode))
		return annotationTextMap.get(hashcode);
		return "";
	}

	/**
	 * Set the text of annotation.
	 * @param hascode of annotation whose text is to be set
	 * @param size set the text of annotation
	 */
	public void setAnnotationText(int hashcode, String text) {
		annotationTextMap.put(hashcode, text);
	}

	/**
	 * Get the node shape
	 *
	 * @param nodeID the ID of the node whose shape is to retrieve
	 * @return the node shape property
	 */
	public NodeShape getNodeShape(long nodeID) {
		if (nodeShapeMap.containsKey(nodeID))
			return nodeShapeMap.get(nodeID);
		return null;
	}

	/**
	 * Set the node shape for a node in this frame
	 *
	 * @param nodeID the ID of the node whose shape is to set
	 * @param shape a NodeShape property for this node
	 */
	public void setNodeShape(long nodeID, NodeShape shape) {
		nodeShapeMap.put(nodeID, shape);
	}

	/**
	 * Get the node position for a node in this frame
	 *
	 * @param nodeID the ID of the node whose position to retrieve
	 * @return the node position as a double array with two values
	 */
	public double[] getNodePosition(long nodeID) {
		if (nodePosMap.containsKey(nodeID))
			return nodePosMap.get(nodeID);
		return null;
	}

	/**
	 * Set the node position for a node in this frame
	 *
	 * @param nodeID the ID of the node whose position to retrieve
	 * @param pos a 2 element double array with the x,y values for this node
	 */
	public void setNodePosition(long nodeID, double[] pos) {
		nodePosMap.put(nodeID, pos);
	}

	/**
	 * Get the node color for a node in this frame
	 *
	 * @param nodeID the ID of the node whose color to retrieve
	 * @return the color 
	 */
	public Color getNodeColor(long nodeID) {
		if (nodeColMap.containsKey(nodeID))
			return nodeColMap.get(nodeID);
		return null;
	}

	/**
	 * Get the node color for a node in this frame
	 *
	 * @param nodeID the ID of the node whose color to retrieve
	 * @return the color 
	 */
	public Color getNodeFillColor(long nodeID) {
		if (nodeFillColMap.containsKey(nodeID))
			return nodeFillColMap.get(nodeID);
		return null;
	}

	/**
	 * Set the node color for a node in this frame
	 *
	 * @param nodeID the ID of the node whose color to retrieve
	 * @param color the color for this node
	 */
	public void setNodeColor(long nodeID, Color color) {
		nodeColMap.put(nodeID, color);
	}

	/**
	 * Set the node color for a node in this frame
	 *
	 * @param nodeID the ID of the node whose color to retrieve
	 * @param color the color for this node
	 */
	public void setNodeFillColor(long nodeID, Color color) {
		nodeFillColMap.put(nodeID, color);
	}

	/**
	 * Get the edge color for an edge in this frame
	 *
	 * @param edgeID the ID of the edge whose color to retrieve
	 * @return the color 
	 */
	public Color getEdgeColor(long edgeID) {
		if (edgeColMap.containsKey(edgeID))
			return edgeColMap.get(edgeID);
		return null;
	}

	/**
	 * Get the edge color for an edge in this frame
	 *
	 * @param edgeID the ID of the edge whose color to retrieve
	 * @return the color 
	 */
	public Color getEdgeStrokeColor(long edgeID) {
		if (edgeStrokeColMap.containsKey(edgeID))
			return edgeStrokeColMap.get(edgeID);
		return null;
	}

	/**
	 * Set the edge color for a edge in this frame
	 *
	 * @param edge the ID of the edge whose color to retrieve
	 * @param color the color for this edge
	 */
	public void setEdgeColor(long edgeID, Color color) {
		edgeColMap.put(edgeID, color);
	}

	/**
	 * Set the edge color for a edge in this frame
	 *
	 * @param edge the ID of the edge whose color to retrieve
	 * @param color the color for this edge
	 */
	public void setEdgeStrokeColor(long edgeID, Color color) {
		edgeStrokeColMap.put(edgeID, color);
	}

	/**
	 * Get the edge opacity for an edge in this frame
	 *
	 * @param edgeID the ID of the edge whose opacity to retrieve
	 * @return the opacity 
	 */
	public Integer getEdgeOpacity(long edgeID) {
		if (edgeOpacityMap.containsKey(edgeID))
			return edgeOpacityMap.get(edgeID);
		return new Integer(0);
	}

	/**
	 * Get the edge opacity for an edge in this frame
	 *
	 * @param edgeID the ID of the edge whose opacity to retrieve
	 * @return the opacity 
	 */
	public Integer getEdgeStrokeOpacity(long edgeID) {
		if (edgeStrokeOpacityMap.containsKey(edgeID))
			return edgeStrokeOpacityMap.get(edgeID);
		return new Integer(0);
	}

	/**
	 * Set the edge opacity for an edge in this frame
	 *
	 * @param edge the ID of the edge whose opacity to retrieve
	 * @param opacity the opacity for this edge
	 */
	public void setEdgeOpacity(long edgeID, Integer opacity) {
		edgeOpacityMap.put(edgeID, opacity);
	}

	/**
	 * Set the edge opacity for an edge in this frame
	 *
	 * @param edge the ID of the edge whose opacity to retrieve
	 * @param opacity the opacity for this edge
	 */
	public void setEdgeStrokeOpacity(long edgeID, Integer opacity) {
		edgeStrokeOpacityMap.put(edgeID, opacity);
	}

	/**
	 * Get the node opacity for a node in this frame
	 *
	 * @param nodeID the ID of the node whose opacity to retrieve
	 * @return the opacity 
	 */
	public Integer getNodeOpacity(long nodeID) {
		if (nodeOpacityMap.containsKey(nodeID))
			return nodeOpacityMap.get(nodeID);
		return new Integer(0);
	}

	/**
	 * Get the node opacity for a node in this frame
	 *
	 * @param nodeID the ID of the node whose opacity to retrieve
	 * @return the opacity 
	 */
	public Integer getNodeFillOpacity(long nodeID) {
		if (nodeFillOpacityMap.containsKey(nodeID))
			return nodeFillOpacityMap.get(nodeID);
		return new Integer(0);
	}

	/**
	 * Set the node opacity for an node in this frame
	 *
	 * @param node the ID of the node whose opacity to retrieve
	 * @param opacity the opacity for this node
	 */
	public void setNodeOpacity(long nodeID, Integer opacity) {
		nodeOpacityMap.put(nodeID, opacity);
	}

	/**
	 * Set the node opacity for an node in this frame
	 *
	 * @param node the ID of the node whose opacity to retrieve
	 * @param opacity the opacity for this node
	 */
	public void setNodeFillOpacity(long nodeID, Integer opacity) {
		nodeFillOpacityMap.put(nodeID, opacity);
	}

	/**
	 * Gets node size of an individual node
	 * 
	 * @param nodeID
	 * @return node size
	 */
	public double[] getNodeSize(long nodeID) {
		if (nodeSizeMap.containsKey(nodeID)) {
			double[] size = nodeSizeMap.get(nodeID);
			return size;
		}
		return null;
	}

	/**
	 * Sets node size of an individual node
	 * 
	 * @param nodeID
	 * @param size
	 */
	public void setNodeSize(long nodeID, double[] size){
		nodeSizeMap.put(nodeID, size);
	}

	/**
	 * 
	 * @param nodeID
	 * @return width of node border
	 */
	public double getNodeBorderWidth(long nodeID){
		if (nodeBorderWidthMap.containsKey(nodeID))
			return nodeBorderWidthMap.get(nodeID);
		return 0.0f;
	}

	/**
	  * 
	  * @param nodeID
	  * @param width
	  */
	public void setNodeBorderWidth(long nodeID, double width){
		nodeBorderWidthMap.put(nodeID, width);
	}

	/**
	 * 
	 * @param nodeID
	 * @return node border color
	 */
	public Color getNodeBorderColor(long nodeID) {
		if (nodeBorderColorMap.containsKey(nodeID))
			return nodeBorderColorMap.get(nodeID);
		return null;
	}

	/**
	 * 
	 * @param nodeID
	 * @param color node border color
	 */
	public void setNodeBorderColor(long nodeID, Color color) {
		nodeBorderColorMap.put(nodeID, color);
	}

	/**
	 * 
	 * @param nodeID
	 * @return node border transparency
	 */
	public Integer getNodeBorderTrans(long nodeID) {
		if (nodeBorderTransMap.containsKey(nodeID))
			return nodeBorderTransMap.get(nodeID);
		return null;
	}

	/**
	 * 
	 * @param nodeID
	 * @param trans node border transparency
	 */
	public void setNodeBorderTrans(long nodeID, Integer trans) {
		nodeBorderTransMap.put(nodeID, trans);
	}

	/**
	 * 
	 * @param nodeID
	 * @return node label font size
	 */
	public Integer getNodeLabelFontSize(long nodeID) {
		if (nodeLabelFontSizeMap.containsKey(nodeID))
			return nodeLabelFontSizeMap.get(nodeID);
		return null;
	}

	/**
	 * 
	 * @param nodeID
	 * @param size node label font size
	 */
	public void setNodeLabelFontSize(long nodeID, Integer size) {
		nodeLabelFontSizeMap.put(nodeID, size);
	}

	/**
	 * 
	 * @param nodeID
	 * @return font label font 
	 */
	public Font getNodeLabelFont(long nodeID) {
		if (nodeLabelFontMap.containsKey(nodeID))
			return nodeLabelFontMap.get(nodeID);
		return null;
	}

	/**
	 * 
	 * @param nodeID
	 * @param font node label font
	 */
	public void setNodeLabelFont(long nodeID, Font font) {
		nodeLabelFontMap.put(nodeID, font);
	}

	/**
	 * 
	 * @param nodeID
	 * @return font label width
	 */
	public Double getNodeLabelWidth(long nodeID) {
		if (nodeLabelWidthMap.containsKey(nodeID))
			return nodeLabelWidthMap.get(nodeID);
		return null;
	}

	/**
	 * 
	 * @param nodeID
	 * @param width node label width
	 */
	public void setNodeLabelWidth(long nodeID, Double width) {
		nodeLabelWidthMap.put(nodeID, width);
	}

	/**
	 * 
	 * @param nodeID
	 * @return node label transparency
	 */
	public Integer getNodeLabelTrans(long nodeID) {
		if (nodeLabelTransMap.containsKey(nodeID))
			return nodeLabelTransMap.get(nodeID);
		return null;
	}

	/**
	 * 
	 * @param nodeID
	 * @param trans node label transparency
	 */
	public void setNodeLabelTrans(long nodeID, Integer trans) {
		nodeLabelTransMap.put(nodeID, trans);
	}

	/**
	 *
	 * @param nodeID
	 * @return node label
	 */
	public String getNodeLabel(long nodeID) {
		if (nodeLabelMap.containsKey(nodeID))
			return nodeLabelMap.get(nodeID);
		return null;
	}

	/**
	  *
	  * @param nodeID
	  * @param label
	  */
	public void setNodeLabel(long nodeID, String label){
		nodeLabelMap.put(nodeID, label);
	}

	/**
	 *
	 * @param nodeID
	 * @return node label color
	 */
	public Color getNodeLabelColor(long nodeID) {
		if (nodeLabelColMap.containsKey(nodeID))
			return nodeLabelColMap.get(nodeID);
		return null;
	}

	/**
	  * 
	  * @param nodeID
	  * @param color
	  */
	public void setNodeLabelColor(long nodeID, Color color){
		nodeLabelColMap.put(nodeID, color);
	}

	/**
	 * 
	 * @param edgeID
	 * @return the edge width 
	 */
	public double getEdgeWidth(long edgeID){
		if (edgeWidthMap.containsKey(edgeID))
			return edgeWidthMap.get(edgeID);
		return 0.0f;
	}

	/**
	 * 
	 * @param edgeID
	 * @param width
	 */
	public void setEdgeWidth(long edgeID, double width){
		edgeWidthMap.put(edgeID, width);
	}

	/**
	 *
	 * @param edgeID
	 * @return the edge label
	 */
	public String getEdgeLabel(long edgeID){
		if (edgeLabel.containsKey(edgeID))
			return edgeLabel.get(edgeID);
		return null;
	}

	/**
	 *
	 * @param edgeID
	 * @param label
	 */
	public void setEdgeLabel(long edgeID, String label){
		edgeLabel.put(edgeID, label);
	}

	/**
	 *
	 * @param edgeID
	 * @return edge label color
	 */
	public Color getEdgeLabelColor(long edgeID) {
		if (edgeLabelColMap.containsKey(edgeID))
			return edgeLabelColMap.get(edgeID);
		return null;
	}

	/**
	 *
	 * @param edgeID
	 * @return font label font
	 */
	public Font getEdgeLabelFont(long edgeID) {
		if (edgeLabelFontMap.containsKey(edgeID))
			return edgeLabelFontMap.get(edgeID);
		return null;
	}

	/**
	  * 
	  * @param edgeID
	  * @param font label font
	  */
	public void setEdgeLabelFont(long edgeID, Font font){
		edgeLabelFontMap.put(edgeID, font);
	}

	/**
	 *
	 * @param edgeID
	 * @return edge transparency
	 */
	public Integer getEdgeLabelTrans(long edgeID) {
		if (edgeLabelTransMap.containsKey(edgeID))
			return edgeLabelTransMap.get(edgeID);
		return null;
	}

	/**
	  * 
	  * @param edgeID
	  * @param color
	  */
	public void setEdgeLabelColor(long edgeID, Color color){
		edgeLabelColMap.put(edgeID, color);
	}

	/**
	 *
	 * @param edgeID
	 * @return edge label size
	 */
	public Integer getEdgeLabelFontSize(long edgeID) {
		if (edgeLabelFontSizeMap.containsKey(edgeID))
			return edgeLabelFontSizeMap.get(edgeID);
		return null;
	}

	/**
	  * 
	  * @param edgeID
	  * @param size font size
	  */
	public void setEdgeLabelFontSize(long edgeID, Integer size){
		edgeLabelFontSizeMap.put(edgeID, size);
	}

	/**
	  * 
	  * @param edgeID
	  * @param trans transparency
	  */
	public void setEdgeLabelTrans(long edgeID, Integer trans){
		edgeLabelTransMap.put(edgeID, trans);
	}

	/**
	 *
	 * @param edgeID
	 * @return edge source arrow shape
	 */
	public ArrowShape getEdgeSourceArrowShape(long edgeID) {
		if (edgeSourceArrowShapeMap.containsKey(edgeID))
			return edgeSourceArrowShapeMap.get(edgeID);
		return null;
	}

	/**
	  *
	  * @param edgeID
	  * @param shape arrow shape
	  */
	public void setEdgeSourceArrowShape(long edgeID, ArrowShape shape){
		edgeSourceArrowShapeMap.put(edgeID, shape);
	}

	/**
	 *
	 * @param edgeID
	 * @return edge target arrow shape
	 */
	public ArrowShape getEdgeTargetArrowShape(long edgeID) {
		if (edgeTargetArrowShapeMap.containsKey(edgeID))
			return edgeTargetArrowShapeMap.get(edgeID);
		return null;
	}

	/**
	  *
	  * @param edgeID
	  * @param shape arrow shape
	  */
	public void setEdgeTargetArrowShape(long edgeID, ArrowShape shape){
		edgeTargetArrowShapeMap.put(edgeID, shape);
	}

	/**
	 *
	 * @param edgeID
	 * @return edge line type
	 */
	public LineType getEdgeLineType(long edgeID) {
		if (edgeLineTypeMap.containsKey(edgeID))
			return edgeLineTypeMap.get(edgeID);
		return null;
	}

	/**
	  *
	  * @param edgeID
	  * @param line line type
	  */
	public void setEdgeLineType(long edgeID, LineType line){
		edgeLineTypeMap.put(edgeID, line);
	}

	/**
	 * Get the list of nodes in this frame
	 *
	 * @return the list of nodes
	 */
	public List<CyNode> getNodeList() {
		return nodeList;
	}

	/**
	 * Get the list of edges in this frame
	 *
	 * @return the list of edges
	 */
	public List<CyEdge> getEdgeList() {
		return edgeList;
	}

	/**
	 * Get the list of annotations in this frame
	 *
	 * @return the list of annotations
	 */
	public List<Annotation> getAnnotationList() {
		return annotationList;
	}

	/**
	 * Set the list of nodes in this frame
	 *
	 * @param nodeList the list of nodes
	 */
	public void setNodeList(List<CyNode>nodeList) {
		this.nodeList = nodeList;
	}

	/**
	 * Set the list of edges in this frame
	 *
	 * @param edgeList the list of edges
	 */
	public void setEdgeList(List<CyEdge>edgeList) {
		this.edgeList = edgeList;
	}

	/**
	 * Set the list of edges in this frame
	 *
	 * @param edgeList the list of edges
	 */
	public void setAnnotationList(List<Annotation> annotationList) {
		this.annotationList = annotationList;
	}

	/**
	 * Get the list of node views in this frame
	 *
	 * @return the list of node views
	 */
	public List<Long> getNodeIdList() {
		return nodeIdList;
	}

	/**
	 * Get the list of edge views in this frame
	 *
	 * @return the list of edge views
	 */
	public List<Long> getEdgeIdList() {
		return edgeIdList;
	}

	/**
	 * Get the list of annotations in this frame
	 *
	 * @return the list of annotations
	 */
	public List<Long> getAnnotationIdList() {
		return annotationIdList;
	}

	/**
	 * Set the list of node views in this frame
	 *
	 * @param nodeIdList the list of node views
	 */
	public void setNodeIdList(List<Long>nodeIdList) {
		this.nodeIdList = nodeIdList;
	}

	/**
	 * Set the list of edge views in this frame
	 *
	 * @param edgeIdList the list of edges
	 */
	public void setEdgeIdList(List<Long>edgeIdList) {
		this.edgeIdList = edgeIdList;
	}

	/**
	 * Get the Image for this frame
	 *
	 * @return the image for this frame
	 */
	public BufferedImage getFrameImage() {
		return this.networkImage;
	}

	/**
 	 * Export a graphic image for this frame
 	 *
 	 * @param fileName the file to write the image to
 	 */
	public void writeImage(String fileName, final int videoResolution,final BooleanWrapper finished) throws IOException {
		display();
		// Make sure we let the renderer catch up
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		CyNetworkView view = appManager.getCurrentNetworkView();

		NetworkViewTaskFactory exportImageTaskFactory = (NetworkViewTaskFactory) bundleContext.getService(NetworkViewTaskFactory.class, "(&(commandNamespace=view)(command=export))");
		if (exportImageTaskFactory != null && exportImageTaskFactory.isReady(view)) {
			Map<String, Object> tunables = new HashMap<String, Object>();
			List<String> fileTypeList = new ArrayList<String>();
			fileTypeList.add(PNG);
			ListSingleSelection<String> fileType = new ListSingleSelection<String>(fileTypeList);
			fileType.setSelectedValue(PNG);
			tunables.put("options", fileType);
			tunables.put("OutputFile", new File(fileName));
			tunables.put("Zoom", new BoundedDouble(0.0, (double) videoResolution, (double) videoResolution,true,false));
			taskManager.setExecutionContext(tunables);
			taskManager.execute(exportImageTaskFactory.createTaskIterator(view));
			finished.setValue(true);
		}
		clearDisplay();
	}

	/**
	 * Get the center point for the frame
	 * 
	 * @return the center for this frame
	 */
	public Point3D getCenterPoint() {
		return this.centerPoint;
	}

	/**
	 * Set the center point of the frame
	 * 
	 * @param center point for a frame
	 */
	public void setCenterPoint(Point3D pnt) {
		this.centerPoint = pnt;
	}

	/**
	 * Returns the BundleContext of this CyFrame.
	 * @return the BundleContext of this CyFrame.
	 */
	public CyServiceRegistrar getBundleContext() {
		return bundleContext;
	}

	private void setAnnotationSize(ShapeAnnotation ann) {
		Double width = new Double(ann.getArgMap().get(ShapeAnnotation.WIDTH));
		Double height = new Double(ann.getArgMap().get(ShapeAnnotation.HEIGHT));
		Dimension d = new Dimension();
		d.setSize(width, height);
		annotationSizeMap.put(ann.hashCode(), d);
	}

}
