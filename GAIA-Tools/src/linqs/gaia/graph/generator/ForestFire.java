/*
* This file is part of the GAIA-Tools software.
* Copyright 2011 University of Maryland
* 
* GAIA-Tools is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* GAIA-Tools is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GAIA-Tools.  If not, see <http://www.gnu.org/licenses/>.
* 
*/
package linqs.gaia.graph.generator;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import linqs.gaia.configurable.BaseConfigurable;
import linqs.gaia.exception.ConfigurationException;
import linqs.gaia.exception.InvalidStateException;
import linqs.gaia.feature.schema.Schema;
import linqs.gaia.feature.schema.SchemaType;
import linqs.gaia.graph.DirectedEdge;
import linqs.gaia.graph.Edge;
import linqs.gaia.graph.Graph;
import linqs.gaia.graph.GraphUtils;
import linqs.gaia.graph.Node;
import linqs.gaia.graph.datagraph.DataGraph;
import linqs.gaia.identifiable.GraphID;
import linqs.gaia.identifiable.GraphItemID;
import linqs.gaia.log.Log;
import linqs.gaia.util.Dynamic;
import linqs.gaia.util.IteratorUtils;
import linqs.gaia.util.ListUtils;
import linqs.gaia.util.SimpleTimer;
import umontreal.iro.lecuyer.probdist.GeometricDist;
import umontreal.iro.lecuyer.randvar.GeometricGen;
import umontreal.iro.lecuyer.rng.LFSR113;

/**
 * Forest fire generation model based on:
 * <p>
 * Leskovec, J., Kleinberg, J., and Faloutsos, C. 2007.
 * Graph evolution: Densification and shrinking diameters.
 * ACM Trans. Knowl. Discov. Data 1, 1 (Mar. 2007), 2.
 * DOI= http://doi.acm.org/10.1145/1217299.1217301
 * <p>
 * Note: This generator requires the SSJ Library to be in
 * the class path (i.e., ssj.jar).
 * <p>
 * 
 *  Optional Parameters:
 *  <UL>
 *  <LI> graphclass-Full java class for the graph,
 * instantiated using Dynamic.forConfigurableName.
 * Defaults is {@link linqs.gaia.graph.datagraph.DataGraph}.
 *  <LI> isdirected-If no, create a graph with undirected edges by creating
 *  a directed edge between all nodes which have a directed edge between them.
 *  (i.e., an undirected edge added between a-b if a->b and/or b->a exists).
 *  By default, create directed.
 *  <LI> numnodes-Number of nodes to generate for the given graph.  Default is 1000.
 *  <LI> pf-Forest Þre forward burning probability.  Default is .37.
 *  <LI> pb-Forest fire backbard burning probability.  Default is .32.
 *  <LI> duplinks-If yes, allow duplicate links
 *  (i.e., two directed edges have the same source to target).  Default is no.
 *  <LI> nodeschemaid-Schema id for nodes.  Default is ffnode.
 *  <LI> edgeschemaid-Schema id for edge.  Default is ffedge.
 *  <LI> graphschemaid-Schema id for graph.  Default is ffgraph.
 *  <LI> graphobjid-Object id for graph.  Default is g1.
 *  <LI> seed-Random generator seed.  Default is 0.
 *  </UL>
 * 
 * @author mbilgic
 * @author namatag
 *
 */
public class ForestFire extends BaseConfigurable implements Generator {
	private Random rand = null;
	private GeometricGen geometricX = null;
	private GeometricGen geometricY = null;
	private int nodekeyid = 0;
	private int edgekeyid = 0;
	
	private int numnodes=1000;
	private boolean isdirected = true;
	private boolean duplinks = false;
	private double pf = .4;
	private double pb = .2;
	private double r = pb/pf;
	private int seed = 0;
	
	private String graphobjid = "g1";
	private String graphschemaid = "ffgraph";
	private String nodeschemaid = "ffnode";
	private String edgeschemaid = "ffedge";
	private String savedesid = "ffedgetemp";
	
	private SimpleTimer timer = new SimpleTimer();
	
	public Graph generateGraph() {
		return this.generateGraph(null);
	}
	
	public Graph generateGraph(String objid) {
		// Get parameters
		if(this.hasParameter("numnodes")) {
			numnodes = (int) this.getDoubleParameter("numnodes");
		}
		
		if(this.hasParameter("pf")) {
			pf = this.getDoubleParameter("pf");
		}
		
		if(this.hasParameter("pb")) {
			pb = this.getDoubleParameter("pb");
		}
		
		// Ratio of backward burning probability/forward burning
		// Note:  The paper page 30 has pb = r*pf while on page
		// 19 r = pf/pb.  This seems to be a misprint.
		r = pb/pf;
		
		if(this.hasParameter("nodeschemaid")) {
			this.nodeschemaid = this.getStringParameter("nodeschemaid");
		}
		
		if(this.hasParameter("edgeschemaid")) {
			this.edgeschemaid = this.getStringParameter("edgeschemaid");
		}
		
		if(this.hasParameter("graphschemaid")) {
			this.graphschemaid = this.getStringParameter("graphschemaid");
		}
		
		if(objid!=null) {
			this.graphobjid=objid;
		} else if(this.hasParameter("graphobjid")) {
			this.graphobjid = this.getStringParameter("graphobjid");
		}
		
		if(this.hasParameter("isdirected", "yes")) {
			isdirected = true;
		} else if(this.hasParameter("isdirected", "no")) {
			isdirected = false;
			
			// Save the schema id to use for later
			this.savedesid = this.edgeschemaid;
			
			// Use a temporary schema id for now
			this.edgeschemaid = "tmpffdir";
		} else if(this.hasParameter("isdirected")) {
			throw new ConfigurationException("Invalid isdirected option: "
					+this.getStringParameter("isdirected"));
		}
		
		if(this.hasParameter("duplinks", "yes")) {
			this.duplinks = true;
		} else if(this.hasParameter("duplinks", "no")) {
			this.duplinks = false;
		} else if(this.hasParameter("duplinks")) {
			throw new ConfigurationException("Invalid duplinks option: "
					+this.getStringParameter("duplinks"));
		}
		
		if(this.hasParameter("seed")) {
			seed = (int) this.getDoubleParameter("seed");
		}
		
		// Generate graph
		Graph graph = generateJustGraph();
		
		if(!this.isdirected) {
			this.convertDir2Undir(graph, this.edgeschemaid, this.savedesid);
		}
		
		Log.INFO(GraphUtils.getSimpleGraphOverview(graph));
		
		return graph;
	}
	
	/**
	 * Generate graph
	 * 
	 * @return Generated Graph
	 */
	public Graph generateJustGraph(){
		SimpleTimer gengraphtimer = new SimpleTimer();
		
		// Create graph
		GraphID gid = new GraphID(graphschemaid,this.graphobjid);
		String graphclass = DataGraph.class.getCanonicalName();
		if(this.hasParameter("graphclass")){
			graphclass = this.getStringParameter("graphclass");
		}

		Class<?>[] argsClass = new Class[]{GraphID.class};
		Object[] argValues = new Object[]{gid};
		Graph graph = (Graph) Dynamic.forName(Graph.class,
				graphclass,
				argsClass,
				argValues);
		
		graph.copyParameters(this);
		
		rand = new Random(seed);
		
		// Create node schema
		graph.addSchema(nodeschemaid, new Schema(SchemaType.NODE));
		
		// Create edge schema
		graph.addSchema(edgeschemaid, new Schema(SchemaType.DIRECTED));
		
		// Note: GeometricGen requires a pvalue, not the mean described in
		// page 26.  Since mean = 1-pvalue/pvalue, the equivalent
		// pvalue of pf/(1.0-pf) and r*pf/(1.0-(r*pf)) are as follows:
		double geometricXP = 1-pf;
		double geometricYP = 1-(r*pf);
		LFSR113.setPackageSeed(new int[]{Math.abs(rand.nextInt(Integer.MAX_VALUE))+2,
										Math.abs(rand.nextInt(Integer.MAX_VALUE))+8,
										Math.abs(rand.nextInt(Integer.MAX_VALUE))+16,
										Math.abs(rand.nextInt(Integer.MAX_VALUE))+128});
		geometricX = new GeometricGen(new LFSR113(), new GeometricDist(geometricXP));
		geometricY = new GeometricGen(new LFSR113(), new GeometricDist(geometricYP));
		
		timer.start();
		for(int i=0;i<this.numnodes;i++){
			GraphItemID giid = new GraphItemID(gid, nodeschemaid, ""+(nodekeyid++));
			
			// Create nodes
			Node node = graph.addNode(giid);
			
			// Don't connect first node
			if(graph.numNodes()==1) {
				continue;
			}
			
			// Connect node to other nodes, as appropriate
			connectToOtherNodes(node, graph);
		}		
		
		Log.INFO("Time to generate graph: "+gengraphtimer.timeLapse(true));
		
		return graph;
	}

	/**
	 * Connect node to other nodes, as defined by forest fire model.
	 * 
	 * @param source Source node
	 * @param graph Graph node belongs to
	 */
	private void connectToOtherNodes(Node source, Graph graph) {
		// Keep a list of visited nodes
		Set<Node> nodesVisited = new HashSet<Node>();
		nodesVisited.add(source);
		
		Node ambassadorNode = this.getAmbassadorNode(graph, source);
		
		LinkedList<Node> connectNodes = new LinkedList<Node>();
		connectNodes.add(ambassadorNode);
		Set<Node> prevtargets = new HashSet<Node>();
		
		while(!connectNodes.isEmpty()){
			Node dest = connectNodes.removeLast();
			nodesVisited.add(dest);
			
			// Number of links to follow from this node
			int x = geometricX.nextInt();
			int y = geometricY.nextInt();
			
			// Chase x outlinks
			List<Edge> pickedLinks = null;
			if(x!=0){
				List<Edge> outlinks =
					IteratorUtils.iterator2edgelist(dest.getEdgesWhereSource(edgeschemaid));
				
				if(outlinks.size() <= x) {
					pickedLinks = outlinks;
				} else {
					@SuppressWarnings("unchecked")
					List<Edge> pickKAtRandom =
						(List<Edge>) ListUtils.pickKAtRandom(outlinks,x,rand);
					pickedLinks = pickKAtRandom;
				}
				
				// Add the other ends to the connectNodes list
				for(Edge e: pickedLinks){
					Node bdn = ((DirectedEdge) e).getTargetNodes().next();
					if(!nodesVisited.contains(bdn)){
						connectNodes.add(bdn);
					}
				}
			}
			
			// Chase y inlinks
			if(y!=0){
				List<Edge> inlinks = (List<Edge>)
					IteratorUtils.iterator2edgelist(dest.getEdgesWhereTarget(edgeschemaid));
				
				if(inlinks.size()<=y) {
					pickedLinks = inlinks;
				} else {
					@SuppressWarnings("unchecked")
					List<Edge> pickKAtRandom = (List<Edge>) ListUtils.pickKAtRandom(inlinks,y,rand);
					pickedLinks = pickKAtRandom;
				}
				
				// Add the other ends to the connectNodes list
				for(Edge e: pickedLinks){
					Node bdn = ((DirectedEdge) e).getSourceNodes().next();
					if(!nodesVisited.contains(bdn)){
						connectNodes.add(bdn);
					}
				}
			}
			
			if(!duplinks) {
				if(prevtargets.contains(dest)) {
					continue;
				}
			}
			
			// Add a link to the graph
			graph.addDirectedEdge(
					new GraphItemID((GraphID) graph.getID(),edgeschemaid,""+(edgekeyid++)),
					source, dest);
			
			prevtargets.add(dest);
			
			if(graph.numEdges()%1000 == 0) {
				Log.INFO("Num Dir Edges: "+graph.numEdges()+" "+timer.timeLapse(true));
				timer.start();
			}
		}
	}
	
	/**
	 * Select an ambassador node
	 * 
	 * @param g Graph
	 * @param source Source node
	 * @return Ambassador node
	 */
	private Node getAmbassadorNode(Graph g, Node source) {
		Node ambassadorNode = null;
		do {
			// Cannot get an ambassador node if there is only one node in graph
			int numnodes = g.numNodes();
			if(numnodes <= 1) {
				throw new InvalidStateException("Insufficient number of nodes to" +
						"choose ambassador node: "+numnodes);
			}
			
			// Randomly choose an ambassador node
			// Note: Assumes that the nodes have numeric id starting from 0 to (number of nodes)-1.
			int ambassadorindex = rand.nextInt(numnodes);
			GraphItemID giid = new GraphItemID((GraphID) g.getID(), nodeschemaid, ""+ambassadorindex);
			ambassadorNode = g.getNode(giid);
			
			if(ambassadorNode==null) {
				throw new InvalidStateException("Invalid ambassador node: "+giid);
			} 
		} while(ambassadorNode.equals(source));
		
		return ambassadorNode;
	}
	
	/**
	 * Convert directed edges to undirected
	 * 
	 * @param g Graph
	 * @param dirsid Directed edge schema ID
	 * @param undirsid Undirected edge schema ID
	 */
	private void convertDir2Undir(Graph g, String dirsid, String undirsid) {
		GeneratorUtils.copyDir2Undir(g, dirsid, undirsid);
		
		g.removeAllGraphItems(dirsid);
		g.removeSchema(dirsid);
	}
}
