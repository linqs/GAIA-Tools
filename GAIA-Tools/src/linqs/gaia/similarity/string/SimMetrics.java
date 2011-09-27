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
