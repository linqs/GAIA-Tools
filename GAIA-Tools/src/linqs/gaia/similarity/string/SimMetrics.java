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
package linqs.gaia.similarity.string;

import uk.ac.shef.wit.simmetrics.similaritymetrics.InterfaceStringMetric;
import linqs.gaia.configurable.BaseConfigurable;
import linqs.gaia.similarity.NormalizedStringSimilarity;
import linqs.gaia.util.Dynamic;

/**
 * Use the string measures defined in SimMetrics
 * (http://sourceforge.net/projects/simmetrics/ and
 * http://www.dcs.shef.ac.uk/~sam/simmetrics.html).
 * Note: This similarity measure requires the SimMetrics library
 * to be in the class path (i.e., simmetrics-1.6.2.jar).
 * <p>
 * Optional Parameters:
 * <UL>
 * <LI>smclass-Get the similarity measure class defined in smclass.
 * Default is uk.ac.shef.wit.simmetrics.similaritymetrics.EuclideanDistance.
 * </UL>
 * 
 * @author namatag
 *
 */
public class SimMetrics extends BaseConfigurable implements NormalizedStringSimilarity {
	private static final long serialVersionUID = 1L;
	
	private InterfaceStringMetric sm = null;
	
	private void initialize() {
		String smclass = "uk.ac.shef.wit.simmetrics.similaritymetrics.EuclideanDistance";
		if(this.hasParameter("smclass")) {
			smclass = this.getStringParameter("smclass");
		}
		
		sm = (InterfaceStringMetric) Dynamic.forName(
				InterfaceStringMetric.class,
				smclass);
	}
	
	public double getNormalizedSimilarity(String item1, String item2) {
		if(sm == null) {
			this.initialize();
		}
		
		return sm.getSimilarity(item1, item2);
	}

	public double getSimilarity(String item1, String item2) {
		return this.getNormalizedSimilarity(item1, item2);
	}
}
