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
package linqs.gaia.graph.generator.decorator;

import java.util.Iterator;
import java.util.Random;

import umontreal.iro.lecuyer.probdist.BinomialDist;
import umontreal.iro.lecuyer.randvar.BinomialGen;
import umontreal.iro.lecuyer.rng.LFSR113;
import linqs.gaia.configurable.BaseConfigurable;
import linqs.gaia.exception.ConfigurationException;
import linqs.gaia.feature.CategFeature;
import linqs.gaia.feature.Feature;
import linqs.gaia.feature.explicit.ExplicitCateg;
import linqs.gaia.feature.explicit.ExplicitNum;
import linqs.gaia.feature.schema.Schema;
import linqs.gaia.feature.values.CategValue;
import linqs.gaia.feature.values.FeatureValue;
import linqs.gaia.feature.values.NumValue;
import linqs.gaia.graph.Graph;
import linqs.gaia.graph.GraphItem;
import linqs.gaia.util.UnmodifiableList;

/**
 * Generate attributes based on labels (explicit categorical features).
 * The attributes are set using a binomial distribution.
 * 
 * Required Parameters:
 * <UL>
 * <LI> schemaid-Schema ID of graph items to set attributes for
 * <LI> targetfeatureid-Feature id of the label feature to add.  Default is "label".
 * </UL>
 * <p>
 * Optional Parameters:
 * <UL>
 * <LI> vocabsize-Number of words to generate. Default is 5.
 * <LI> numobs-Number of observed words.  Default is 2.
 * <LI> attrnoise-Probability of noise over the words.  Default is .25.
 * <LI> attrprefix-Prefix to use in the feature name.  Default is "w".
 * <LI> seed-Random number generator seed.  Default is 0.
 * </UL>
 * 
 * @author mbilgic
 * @author namatag
 *
 */
public class BinomialAttributes extends BaseConfigurable implements Decorator {
	protected String schemaid;
	protected String targetfeatureid;
	protected int vocabsize = 5;
	private int numobs = 2;
	private double attrnoise = .25;
	private int numlabels = -1;
	private String attrprefix = "w";
	private int seed = 0;
	
	public void decorate(Graph g) {
		// Set parameters
		this.schemaid = this.getStringParameter("schemaid");
		this.targetfeatureid = this.getStringParameter("targetfeatureid");
		
		if(this.hasParameter("vocabsize")) {
			this.vocabsize = (int) this.getDoubleParameter("vocabsize");
		}
		
		if(this.hasParameter("numobs")) {
			this.numobs = (int) this.getDoubleParameter("numobs");
		}
		
		if(this.hasParameter("attrnoise")) {
			this.attrnoise = this.getDoubleParameter("attrnoise");
		}
		
		if(this.hasParameter("attrprefix")) {
			this.attrprefix = this.getStringParameter("attrprefix");
		}
		
		if(this.hasParameter("seed")) {
			this.seed = (int) this.getDoubleParameter("seed");
		}
		Random rand = new Random(this.seed);
		
		// Get the label feature
		Schema schema = g.getSchema(schemaid);
		Feature f = schema.getFeature(targetfeatureid);
		if(!(f instanceof ExplicitCateg)) {
			throw new ConfigurationException("Unsupported feature type: "
					+f.getClass().getCanonicalName());
		}
		UnmodifiableList<String> cats = ((CategFeature) f).getAllCategories();
		numlabels = cats.size();
		
		// Update schema to support new attributes
		int totalwords = vocabsize;
		for(int i=0;i<totalwords;i++){
			// Add numeric features for the different words to add
			schema.addFeature(attrprefix+i, new ExplicitNum(new NumValue(0.0)));
		}
		g.updateSchema(schemaid, schema);
		
		// Go over all graph items and add attributes
		Iterator<GraphItem> gitr = g.getGraphItems(schemaid);
		while(gitr.hasNext()) {
			GraphItem gi = gitr.next();
			FeatureValue fvalue = gi.getFeatureValue(targetfeatureid);
			if(fvalue.equals(FeatureValue.UNKNOWN_VALUE)) {
				throw new ConfigurationException("All labels must be known: "+
						gi+"."+targetfeatureid+"="+fvalue);
			}
			
			int labelindex = cats.indexOf(((CategValue) fvalue).getCategory());
			genAttributesBinomial(gi, labelindex, rand);
		}
	}
	
	/**
	 * Generate binomial attributes
	 * 
	 * @param gi Graph item to generate attribute for
	 * @param c Label index
	 * @param rand Random number generator
	 */
	private void genAttributesBinomial(GraphItem gi, int c, Random rand) {
		double probSuccess = (1.0+c)/(1+numlabels);
		
		// Set seed for synthetic data
		LFSR113.setPackageSeed(new int[]{rand.nextInt()+2,
				rand.nextInt()+8,
				rand.nextInt()+16,
				rand.nextInt()+128});
		BinomialGen bigen = new BinomialGen(new LFSR113(), new BinomialDist(vocabsize, probSuccess));
		int[] wordCounts = new int[vocabsize];

		for(int i=0;i<numobs;i++){
			double r = rand.nextDouble();
			if(r<=attrnoise) {
				int word = 0;
				do{
					word = rand.nextInt(vocabsize);
				} while(wordCounts[word]!=0);

				wordCounts[word]++;
			} else {
				int word = 0;

				do {
					word = bigen.nextInt()%vocabsize;
				} while(wordCounts[word]!=0);

				wordCounts[word]++;
			}
		}
		
		for(int i=0;i<vocabsize;i++){
			gi.setFeatureValue(attrprefix+i, new NumValue(0.0 + wordCounts[i]));
		}
	}
}
