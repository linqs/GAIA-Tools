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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import linqs.gaia.configurable.BaseConfigurable;
import linqs.gaia.exception.ConfigurationException;
import linqs.gaia.feature.schema.Schema;
import linqs.gaia.feature.schema.SchemaType;
import linqs.gaia.graph.Graph;
import linqs.gaia.graph.GraphUtils;
import linqs.gaia.graph.datagraph.DataGraph;
import linqs.gaia.identifiable.GraphID;
import linqs.gaia.identifiable.GraphItemID;
import linqs.gaia.log.Log;
import linqs.gaia.util.Dynamic;
import umontreal.iro.lecuyer.probdist.ExponentialDist;
import umontreal.iro.lecuyer.randvar.ExponentialGen;
import umontreal.iro.lecuyer.rng.LFSR113;
import cern.jet.random.engine.MersenneTwister;
import cern.jet.random.engine.RandomEngine;

/**
 * Microscopic Evolution model
 * <p>
 * Jure Leskovec, Lars Backstrom, Ravi Kumar, Andrew Tomkins.
 * Microscopic Evolution of Social Networks.
 * ACM SIGKDD International Conference on Knowledge Discovery and Data Mining (ACM KDD), 2008. 
 * 
 * Optional Parameters:
 * <UL>
 * <LI> graphclass-Full java class for the graph,
 * instantiated using Dynamic.forConfigurableName.
 * Defaults is {@link linqs.gaia.graph.datagraph.DataGraph}.
 * <LI> graphobjid-Object id for graph.  Default is g1.
 * <LI> graphschemaid-Schema ID of a graph.  Default is megraph.
 * <LI> nodeschemaid-Schema ID of the nodes.  Default is menode.
 * <LI> edgeschemaid-Schema ID of the edges.  Default is meedge.
 * <LI> isdirected-If yes, the generated edges are directed.
 * They edges are undirected otherwise.  Default is yes.
 * <LI> nodesPerDay-Initial number of nodes to create per day.
 * Default is 10.
 * <LI> lastEvolDay-Last day of evoluation.  Default is 10.
 * <LI> lambda-Model lambda value.  Default is 0.0092.
 * <LI> alpha-Model alpha value.  Default is 0.84.
 * <LI> beta-Model beta value.  Default is 0.002.
 * <LI> seed-Random generator seed.  Default is 0.
 * </UL>
 * 
 * @author hossam
 * @author elena
 * @author namatag
 *
 */
public class MicroEvolution extends BaseConfigurable implements Generator {
	
	private RandomEngine generator;
	private int currentDay;
	private double alpha;
	private double beta;
	private double lambda;
	private int totalLinks=0;
	private int maxDegree=1;
	private HashMap<Integer,ArrayList<Node>> schNodes;

	// All nodes
	private ArrayList<Node> nodes;
	
	// Counter for use with node ids
	private static int nodecounter = 0;
	
	// Counter for use with edge ids
	private static int edgecounter = 0;
	
	public Graph generateGraph() {
		return this.generateGraph(null);
	}
	
	public Graph generateGraph(String objid) {
		int nodesPerDay = 10;
		if(this.hasParameter("nodesPerDay")) {
			nodesPerDay = this.getIntegerParameter("nodesPerDay");
		}

		int lastEvolDay = 10;
		if(this.hasParameter("lastEvolDay")) {
			lastEvolDay = this.getIntegerParameter("lastEvolDay");
		}

		this.lambda = 0.0092;
		if(this.hasParameter("lambda")) {
			lambda = this.getDoubleParameter("lambda");
		}

		this.alpha = 0.84;
		if(this.hasParameter("alpha")) {
			alpha = this.getDoubleParameter("alpha");
		}

		this.beta = 0.002;
		if(this.hasParameter("beta")) {
			beta = this.getDoubleParameter("beta");
		}
		
		int seed = 0;
		if(this.hasParameter("seed")) {
			seed = this.getIntegerParameter("seed");
		}

		// Initialize
		generator = new MersenneTwister(seed);
		currentDay = 1;
		totalLinks=0;
		maxDegree=1;

		// Create internal graph
		this.evolve(nodesPerDay, lastEvolDay);

		// Create GAIA Graph from internal graph
		String graphsid = "megraph";
		if(this.hasParameter("graphschemaid")) {
			graphsid = this.getStringParameter("graphschemaid");
		}
		
		String graphobjid = "g1";
		if(objid!=null) {
			graphobjid = objid;
		} else if(this.hasParameter("graphobjid")) {
			graphobjid = this.getStringParameter("graphobjid");
		}
		
		String nodesid = "menode";
		if(this.hasParameter("nodeschemaid")) {
			nodesid = this.getStringParameter("nodesid");
		}
		
		String edgesid = "meedge";
		if(this.hasParameter("edgeschemaid")) {
			edgesid = this.getStringParameter("edgeschemaid");
		}
		
		boolean isdirected = true;
		String savedesid = null;
		if(this.hasParameter("isdirected", "yes")) {
			isdirected = true;
		} else if(this.hasParameter("isdirected", "no")) {
			isdirected = false;
			
			// Save the schema id to use for later
			savedesid = edgesid;
			
			// Use a temporary schema id for now
			edgesid = "tmpffdir";
		} else if(this.hasParameter("isdirected")) {
			throw new ConfigurationException("Invalid isdirected option: "
					+this.getStringParameter("isdirected"));
		}
		
		// Create graph
		GraphID gid = new GraphID(graphsid, graphobjid);
		String graphclass = DataGraph.class.getCanonicalName();
		if(this.hasParameter("graphclass")){
			graphclass = this.getStringParameter("graphclass");
		}

		Class<?>[] argsClass = new Class[]{GraphID.class};
		Object[] argValues = new Object[]{gid};
		Graph g = (Graph) Dynamic.forName(Graph.class,
				graphclass,
				argsClass,
				argValues);
		
		g.copyParameters(this);
		
		g.addSchema(nodesid, new Schema(SchemaType.NODE));
		g.addSchema(edgesid, new Schema(SchemaType.DIRECTED));
		
		// Copy all nodes from internal graph
		for(Node n:nodes) {
			g.addNode(new GraphItemID(g.getID(), nodesid, ""+n.getID()));
		}
		
		// Copy all edges from internal graph
		for(Node n:nodes) {
			List<Node> friends = n.friends;
			for(Node f:friends) {
				if(n.equals(f)) {
					continue;
				}
				
				linqs.gaia.graph.Node source = g.getNode(
						new GraphItemID(g.getID(), nodesid, ""+n.getID()));
				linqs.gaia.graph.Node target = g.getNode(
						new GraphItemID(g.getID(), nodesid, ""+f.getID()));
				
				g.addDirectedEdge(new GraphItemID(g.getID(),
						edgesid, (edgecounter++)+""), source, target);
			}
		}

		// Clear all
		this.schNodes.clear();
		this.nodes.clear();
		
		if(!isdirected) {
			this.convertDir2Undir(g, edgesid, savedesid);
		}
		
		Log.INFO("Graph generated: "+GraphUtils.getSimpleGraphOverview(g));

		return g;
	}

	/**
	 * Evolve model
	 * 
	 * @param nodesPerDay
	 * @param lastEvolDay
	 */
	private void evolve(int nodesPerDay, int lastEvolDay){
		Node friend;
		int sleeptime;
		ArrayList<Node> list;
		int key;

		schNodes = new HashMap<Integer, ArrayList<Node>>(lastEvolDay);
		nodes = new ArrayList<Node>(lastEvolDay*nodesPerDay+2); 

		// the first two nodes connect to each other
		Node n1 = new Node(currentDay, lambda);
		sleeptime = contPowerLawExpCutoff(alpha, beta);
		if (sleeptime+currentDay<=n1.getLastDay() && sleeptime < lastEvolDay){
			list = new ArrayList<Node>();
			list.add(n1);
			schNodes.put(sleeptime+currentDay,list);	
		}
		
		Node n = new Node(currentDay, lambda);
		sleeptime = contPowerLawExpCutoff(alpha, beta);
		if (sleeptime+currentDay<=n.getLastDay() && sleeptime < lastEvolDay){
			list = new ArrayList<Node>();
			list.add(n);
			schNodes.put(sleeptime+currentDay,list);	
		}

		n.connect(n1);
		updateTotalLinks(n);
		n1.connect(n);
		updateTotalLinks(n1);
		totalLinks--;

		nodes.add(n1);
		nodes.add(n);

		// go through the days...
		while (currentDay<=lastEvolDay){
			
			Log.INFO("Day "+currentDay+"\tDegree="+maxDegree+"\t#Nodes="+this.nodes.size());

			// Put your explicit node arrival function by adjusting
			// number of nodes per day
			nodesPerDay=(int) Math.floor(5000*Math.exp(0.25/30*currentDay))-nodes.size();
			
			//***** STEP 1: create new nodes and make them connect to someone 
			for (int i=0; i<nodesPerDay; i++){
				// 1a) sample from lifetime as node is created
				n = new Node(currentDay, lambda);
				
				//***** STEP 2: node adds the first edge to node v 
				// with probability proportional to its degree
				Node temp = pickFirstFriend(); 
				n.connect(temp);
				
				// if undirected links
				temp.connect(n);
				updateTotalLinks(n);
				updateTotalLinks(temp);
				totalLinks--;
				nodes.add(n);
				
				// picks a sleeping time
				sleeptime = contPowerLawExpCutoff(alpha, beta);
				key = sleeptime+currentDay;
				if (key<=n.getLastDay() && key<=lastEvolDay){
					// check if there is anything saved for this day
					if (schNodes.containsKey(key)){
						list = schNodes.get(key);
						list.add(n);
						schNodes.put(key, list);
					}
					else{
						list = new ArrayList<Node>();
						list.add(n);
						schNodes.put(sleeptime+currentDay,list);	
					}
				}
			}

			// 2. wake up old nodes if they are due
			// n.sleepTime = getSleepTime(alpha,beta*n.getDegree()) + currentDay;
			if (schNodes.containsKey(currentDay)){
				Enumeration<Node> e = Collections.enumeration(schNodes.get(currentDay));
				while(e.hasMoreElements()){
					// connect to someone you don't know
					n=e.nextElement();
					friend=pickFriend(n);
					if (friend!=null){
						n.connect(friend);
						
						// if undirected links
						friend.connect(n);
						updateTotalLinks(n);
						updateTotalLinks(friend);
						totalLinks--;
					}
					
					// picks a sleeping time
					sleeptime = contPowerLawExpCutoff(alpha, beta*n.getDegree());
					key = sleeptime+currentDay;
					if (key<=n.getLastDay() && key<=lastEvolDay){
						// check if there is anything saved for this day
						if (schNodes.containsKey(key)){
							list = schNodes.get(key);
							list.add(n);
							schNodes.put(key, list);
						}
						else{
							list = new ArrayList<Node>();
							list.add(n);
							schNodes.put(sleeptime+currentDay,list);	
						}
					}
				}
			}
			
			schNodes.remove(currentDay);

			currentDay++;
		}
	}

	private void updateTotalLinks(Node updated){
		totalLinks++;
		if (updated.getDegree()>maxDegree)
			maxDegree=updated.getDegree();
	}

	private Node pickFriend(Node n){
		Node temp=null;
		boolean newfriend=false;
		int attempts = 0;
		// make sure the new friend is not a friend already and is not yourself
		while(newfriend==false){
			int i = (int) Math.floor(generator.raw()*(n.friends.size()));
			temp = n.friends.get(i);
			int j = (int) Math.floor(generator.raw()*(temp.friends.size()));
			if (!n.friends.contains(temp.friends.get(j)) && !n.equals(temp.friends.get(j)))
				return temp.friends.get(j);
			
			// if it cannot find a node 2 hopes away that is not already a friend (infinite loop..),
			// then it would simply not add a friend
			if (attempts > maxDegree*maxDegree)
				return null;
			attempts++;
		}
		return temp;
	}

	/**
	 * Node adds the first edge to node v with probability proportional to its degree  
	 * 
	 * @return
	 */
	private Node pickFirstFriend(){
		boolean found = false;
		Node temp=null;
		int sum=0;
		int n = (int) Math.ceil(generator.raw()*maxDegree);

		while(found==false){
			temp=nodes.get((int) Math.floor(generator.raw()*nodes.size()));
			if (n<=temp.getDegree()+sum)
				found=true;
			else
				sum+=temp.getDegree();
		}

		return temp;
	}

	/**
	 * This function approximates the random number generation from a discrete power-law 
	 * distribution quite well (from Clauset's paper)
	 * 
	 * @param alpha
	 * @return
	 */
	private int contPowerLaw(double alpha){
		double u = generator.raw();
		int xmin=1; 
		return (int) Math.floor(((xmin-1/2)*Math.pow(1-u,-1/(1-alpha))+1/2));
	}


	private int contExponential(double exponent){
		int xmin=1; 
		double u = generator.raw();
		return (int) Math.floor(xmin-(1/exponent)*Math.log(1-u));
	}

	/**
	 * This function approximates the discrete by getting a random number from either the exponential or
	 * the power-law and then rejects according to the difference with the desired power-law distribution
	 * with an exponential cutoff.
	 * <p>
	 * (Clauset's paper suggests always taking the exponential and accepting according to the difference 
	 * with the power-law BUT this doesn't work well at low node degrees/cutoffs).
	 * 
	 * @param alpha
	 * @param cutoff
	 * @return
	 */
	private int contPowerLawExpCutoff(double alpha, double cutoff){
		double xmin=1; 
		double result=0;
		double reject=1;
		while (reject==1){
			// gets a random number from the exponential distribution
			result = contExponential(cutoff);
			// accepts it with a probability that is
			// 1-(the difference between the exponential and the desired distribution)
			if (generator.raw() < Math.pow(result/xmin,-alpha))
				reject=0;
			// gets a random number from the power-law distribution
			if (reject==1){
				result=contPowerLaw(alpha);
				// accepts it with a probability that is
				// 1-(the difference between the power-law and the desired distribution)
				if (generator.raw() < Math.pow(Math.E, -cutoff))
					reject=0;
			}
		}
		
		return (int) result;
	}

	/**
	 * Internal node implementation for use with generator
	 */
	private class Node{
		// This is the last day when the node can connect to someone
		private int lastDay;
		
		// Friends of node
		private ArrayList<Node> friends;
		
		// Unique identifier for nodes
		private int id;

		public Node(int currentDay, double lambda){
			this.id = nodecounter++;
			
			// STEP 1a)
			// sample  from an exponential distribution
			ExponentialGen ng = new ExponentialGen(new LFSR113(), new ExponentialDist(lambda));
			// should give a you number sampled from the distribution.
			ng.nextDouble(); 
			// how many days it will live + the current day
			lastDay = contExponential(lambda) + currentDay;
			friends = new ArrayList<Node>();
		}
		
		public void connect(Node n){
			friends.add(n);
		}

		public int contExponential(double exponent){
			int xmin=1; 
			double u = generator.raw();
			return (int) Math.floor(xmin-(1/exponent)*Math.log(1-u));
		}

		public int getDegree(){
			return friends.size();
		}

		public int getLastDay(){
			return lastDay;
		}
		
		public int getID() {
			return this.id;
		}
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
