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
package linqs.gaia.model.oc.ncc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;

import linqs.gaia.exception.ConfigurationException;
import linqs.gaia.exception.InvalidStateException;
import linqs.gaia.exception.UnsupportedTypeException;
import linqs.gaia.feature.CategFeature;
import linqs.gaia.feature.CompositeFeature;
import linqs.gaia.feature.Feature;
import linqs.gaia.feature.NumFeature;
import linqs.gaia.feature.decorable.Decorable;
import linqs.gaia.feature.derived.composite.CVFeature;
import linqs.gaia.feature.schema.Schema;
import linqs.gaia.feature.values.CategValue;
import linqs.gaia.feature.values.CompositeValue;
import linqs.gaia.feature.values.FeatureValue;
import linqs.gaia.feature.values.NumValue;
import linqs.gaia.log.Log;
import linqs.gaia.util.ArrayUtils;
import linqs.gaia.util.FileIO;
import linqs.gaia.util.IteratorUtils;
import linqs.gaia.util.KeyedCount;
import linqs.gaia.util.ListUtils;
import linqs.gaia.util.SimplePair;
import linqs.gaia.util.SimpleTimer;
import linqs.gaia.util.UnmodifiableList;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SparseInstance;
import weka.core.converters.CSVSaver;

/**
 * A wrapper to the Weka library.  This allows GAIA to use
 * any Weka classifier as a vector based classifier.
 * This class requires the Weka library to be in the class path
 * (i.e., weka.jar).
 * <p>
 * Optional Parameters:
 * <UL>
 * <LI>wekaclassifier-Weka Classifier to use (i.e., weka.classifiers.functions.Logistic).
 * Default is weka.classifiers.trees.J48.
 * <LI>includefeatures-The parameters is treated as a
 * comma delimited list of feature ids and/or regex pattern
 * for feature IDs in the form REGEX:&lt;pattern&gt;
 * (e.g., color,size,REGEX:\\d,length).  By default,
 * all features, except the target feature, is used.
 * <LI>excludefeatures-Same format as include features
 * but any matching feature id and/or regex pattern
 * is removed.
 * <LI>wekaparams-Comma delimited parameters to pass to weka classifier (i.e., -M,100).
 * Set to NO_PARAMS if no parameters are needed.  Default is set to NO_PARAMS.
 * <LI>printwekamodel-If "yes", print the weka model.
 * <LI>costbyclass-If "yes", add a cost matrix to the weka parameters.  For use with
 * weka classifiers that can use a cost matrix (i.e., MetaCost).  The weight matrix
 * is calculated as follows: For two labels, a and b, if number of instances with label
 * a, |a|, in training is greater than the number of training instances of b, |b|, the cost of
 * misclassifying a as b is 1. The cost of misclassifying b as a, however, is equal to
 * the |b|/|a|.
 * <LI>wekatrainfile-File to print the weka training instances to.  If a file with the name already
 * exists, a numeric suffix is added to the new file.
 * <LI>wekatestfile-File to print the weka testing instances to.  If a file with the name already
 * exists, a numeric suffix is added to the new file.  If the prediction is made over a single
 * instance (i.e., predict(Decorable testitem)), the id of the single instance is appended to the filename.
 * </UL>
 * 
 * @author namatag
 *
 */
public class WekaClassifier extends BaseVBClassifier implements VBClassifier {
	private static final long serialVersionUID = 1L;
	
	private static String NO_PARAMS = "NO_PARAMS";
	private static String DEFAULT_WEKA_CLASSIFIER = "weka.classifiers.trees.J48";
	private Classifier wekaclassifier;

	private UnmodifiableList<String> targetcategories;
	private KeyedCount<String> fclasscount;
	
	private int attinfosize = 0;
	private Instances instances = null;
	
	@Override
	public void learn(Iterable<? extends Decorable> trainitems,
			String targetschemaid, String targetfeatureid, List<String> featureids) {
		try {
			this.targetschemaid = targetschemaid;
			this.targetfeatureid= targetfeatureid;
			this.featureids = new LinkedList<String>(featureids);
			
			LinkedHashSet<String> uniquefids = new LinkedHashSet<String>(featureids);
			if(uniquefids.size() != featureids.size()) {
				Log.WARN("Duplicate feature ids found in set of features: "+featureids);
				this.featureids = new ArrayList<String>(uniquefids);
			}
			
			if(this.featureids.contains(this.targetfeatureid)) {
				throw new InvalidStateException(
						"Cannot include target feature as a dependency feature: "
						+this.targetfeatureid);
			}
			Log.DEBUG("Features Used: "+ListUtils.list2string(featureids,","));
			
			// Added for weka.  Will only be used for training.
			// Target will not be used as a feature itself.
			this.featureids.add(this.targetfeatureid);

			String wcclass = WekaClassifier.DEFAULT_WEKA_CLASSIFIER;
			if(this.hasParameter("wekaclassifier")) {
				wcclass = this.getStringParameter("wekaclassifier");
			}
			
			String wekaparams = WekaClassifier.NO_PARAMS;
			if(this.hasParameter("wekaparams")) {
				wekaparams = this.getStringParameter("wekaparams");
			}
			boolean printwekamodel = this.hasParameter("printwekamodel","yes");

			// Support generation of class based cost matrix
			if(this.hasParameter("costbyclass","yes")){
				fclasscount = new KeyedCount<String>();
			}

			// Weka instances
			int numinstances = IteratorUtils.numIterable(trainitems);
			Instances traininstances = this.gaia2weka(trainitems.iterator(), numinstances, false);

			// Handle class based cost matrix
			if(fclasscount != null){
				if(wekaparams.equals(WekaClassifier.NO_PARAMS)){
					wekaparams = "";
				} else {
					wekaparams += ",";
				}

				wekaparams += "-cost-matrix,"+this.getCostMatrix();
			}

			// Set GAIA parameters and initialize classifier
			String params[] = null;
			if(!wekaparams.equals(WekaClassifier.NO_PARAMS)){
				Log.DEBUG("Using wekaparams: " + wekaparams);
				params = wekaparams.split(",");
			}
			wekaclassifier = Classifier.forName(wcclass, params);

			// Train classifier
			if(this.hasParameter("wekatrainfile")){
				String savefile = this.getStringParameter("wekatrainfile");
				this.saveWekaInstances(savefile, traininstances);
			}

			Log.DEBUG("Weka building classifier");
			SimpleTimer st = new SimpleTimer();
			st.start();
			wekaclassifier.buildClassifier(traininstances);
			Log.DEBUG("Weka done building classifier: ("+st.timeLapse(true)+")");

			// Print Weka Model, if requested
			if(printwekamodel) {
				Log.INFO("Learned Weka Model:\n"+this.wekaclassifier);
			}

			// Print attributes
			if(Log.SHOWDEBUG) {

				String features = null;
				for(int f=0; f<traininstances.numAttributes(); f++){
					if(features==null){
						features = "";
					} else {
						features += ",";
					}

					features += traininstances.attribute(f).name();
				}

				String options[] = wekaclassifier.getOptions();
				Log.DEBUG("Weka Options: "+ArrayUtils.array2String(options,","));
			}
			
			// Clear instances once training is complete
			traininstances.delete();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Generate a class based cost matrix
	 * 
	 * @param fclasscount
	 * @return
	 */
	private String getCostMatrix() {
		StringBuffer buf = new StringBuffer();
		buf.append("[");
		
		int numcats = this.targetcategories.size();

		// Only defined for more than two categories
		if(numcats<2){
			throw new ConfigurationException("Invalid number of categories: "+numcats);
		}

		for(int i=0; i<numcats; i++){
			int icount = (int) fclasscount.getCount(this.targetcategories.get(i));

			if(i!=0){
				buf.append(";");
			}

			// To avoid divide by zero, add 1 to each count
			icount++;
			for(int j=0; j<numcats; j++){	
				int jcount = (int) fclasscount.getCount(this.targetcategories.get(j));

				// To avoid divide by zero, add 1 to each count
				jcount++;

				if(!(i==0 && j==0)){
					buf.append(" ");
				}

				if(i==j){
					buf.append("0.0");
				} else {
					if(icount >= jcount){
						buf.append("1.0");
					} else {
						double normcost = (double) jcount / icount;
						buf.append(normcost);
					}
				}
			}
		}

		buf.append("]");

		return buf.toString();
	}

	/**
	 * Convert gaia items to weka instances
	 * 
	 * @param items GAIA Decorable items
	 * @param numinstances Number of instances
	 * @param ispredict Is the instances being created for prediction or training
	 * @return Weka Instances
	 */
	private Instances gaia2weka(Iterator<? extends Decorable> items,
			int numinstances, boolean ispredict) {
		
		// Get the first item to get graph information
		Decorable first = items.next();
		
		// Convert GAIA objects to Weka instances
		SimpleTimer st = new SimpleTimer();
		
		if(instances==null) {
			this.createInstances(first);
		}
		
		// Clear old information from instances
		instances.delete();

		// Add first item
		this.createInstance(instances, first, ispredict);

		// Add all other items 
		// Note: Counter starting at 1 since the first item already inserted above
		int gicounter = 1;
		while(items.hasNext()) {
			Decorable di = items.next();
			
			gicounter++;
			if(Log.SHOWDEBUG && (gicounter%1000==0 || !items.hasNext())){
				Log.DEBUG("Converting GI: "+gicounter
						+" of "+numinstances
						+" Time="+st.timeLapse(true));
				st.start();
			}

			this.createInstance(instances, di, ispredict);
		}

		return instances;
	}
	
	/**
	 * Convert a single gaia item to a weka instance
	 * 
	 * @param item GAIA Decorable item
	 * @param numinstances Number of instances
	 * @param ispredict Is the instances being created for prediction or training
	 * @return Weka Instances
	 */
	private Instances gaia2weka(Decorable item, boolean ispredict) {
		if(instances==null) {
			this.createInstances(item);
		}
		
		// Clear the current value of instances
		instances.delete();

		// Add item
		this.createInstance(instances, item, ispredict);

		return instances;
	}
	
	/**
	 * Create Weka Instances container object
	 * 
	 * @param item Decorable Item to get instance and feature information from
	 */
	private void createInstances(Decorable item) {
		// Get the schema to get graph information
		Schema schema = item.getSchema();
	
		// Add features
		FastVector attInfo = new FastVector();
		Attribute targetattr = null;
		for(String fid:featureids) {
			String fname = fid;
			Feature f = schema.getFeature(fid);
			
			if(!(f instanceof CompositeFeature)) {
				// Handle non-composite features
				Attribute newattr = this.gaiafeatures2weka(fname, f, attInfo);
	
				if(newattr != null) {
					targetattr = newattr;
				}
			} else {
				// Handle composite features
				// In weka, we add features within the composite feature
				// as multiple features.
				CompositeFeature mvf = (CompositeFeature) f;
				UnmodifiableList<SimplePair<String, CVFeature>> mvfeatures = mvf.getFeatures();
				for(SimplePair<String, CVFeature> sp: mvfeatures) {
					String newname = fname+":"+sp.getFirst();
					this.gaiafeatures2weka(newname, sp.getSecond(), attInfo);
				}
			}
		}
		
		// Add target feature to end
		if(targetattr==null){
			throw new ConfigurationException("Target attribute not found: "+this.targetfeatureid);
		}
		attInfo.addElement(targetattr);
		
		// Create Instances object
		instances = new Instances("prediction", attInfo, 1);
		
		// Set class attribute
		instances.setClass(targetattr);
		
		// Save number of Weka attributes
		this.attinfosize = attInfo.size();
	}

	/**
	 * Convert GAIA feature to Weka attribute.  Adds the resulting
	 * to the included Fast Vector
	 * 
	 * @param fname Attribute name
	 * @param f GAIA Feature
	 * @param attInfo Fast vector to store new Weka attribute
	 * 
	 * @return The target weka attribute.  If the current feature is not the target, return null.
	 */
	private Attribute gaiafeatures2weka(String fname, Feature f, FastVector attInfo) {
		Attribute newattr = null;
		Attribute targetattr = null;
		if(f instanceof NumFeature){
			// Handle numeric features
			newattr = new Attribute(fname);
			attInfo.addElement(newattr);
		} else if(f instanceof CategFeature){
			// Handle categorical features
			CategFeature cf = (CategFeature) f;
			UnmodifiableList<String> cats = cf.getAllCategories();
			FastVector acats = new FastVector(cats.size());
			for(String cat:cats){
				acats.addElement(cat);
			}

			newattr = new Attribute(fname, acats);

			if(fname.equals(this.targetfeatureid)){
				this.targetcategories = cats;
				targetattr = newattr;
			} else {
				attInfo.addElement(newattr);
			}
		} else {
			throw new UnsupportedTypeException("Unsupported feature type: "
					+f.getClass().getCanonicalName());
		}

		return targetattr;
	}

	/**
	 * Create Weka instance
	 * 
	 * @param intances Weka instances
	 * @param di Decorable item to convert
	 * @param attInfo Weka attributes
	 * @param ispredict Is this item created for training or testing
	 */
	private void createInstance(Instances instances, Decorable di, boolean ispredict) {
		double[] instvalues = new double[attinfosize];
		int attindex = 0;
		
		Schema schema = di.getSchema();
		for(String fid:featureids) {
			FeatureValue fvalue = di.getFeatureValue(fid);
			Attribute a = instances.attribute(attindex);
			
			Feature f = schema.getFeature(fid);		
			if(!(f instanceof CompositeFeature)) {
				// Handle non multi-valued feature
				instvalues[attindex] = this.gaiavalues2weka(f, fid, fvalue, a, ispredict);
				attindex++;
			} else {
				// Handle multi-valued feature
				CompositeFeature mv = (CompositeFeature) f;
				UnmodifiableList<SimplePair<String,CVFeature>> mvfeatures = mv.getFeatures();
				CompositeValue mvvalue = (CompositeValue) di.getFeatureValue(fid);
				UnmodifiableList<FeatureValue> mvfvalues = mvvalue.getFeatureValues();
				int num = mvfvalues.size();
				for(int j=0;j<num;j++){
					if(fvalue.equals(FeatureValue.UNKNOWN_VALUE)) {
						attindex++;
						continue;
					}
					
					a = instances.attribute(attindex);
					f = mvfeatures.get(j).getSecond();
					fvalue = mvfvalues.get(j);
					instvalues[attindex] = this.gaiavalues2weka(f, fid, fvalue, a, ispredict);
					attindex++;
				}
			}
		}
		
		// Create instance of weight 1 and the specified values
		Instance inst = new SparseInstance(1, instvalues);
		inst.setDataset(instances);

		instances.add(inst);
	}
	
	/**
	 * Set the specified feature value as the Weka attribute value
	 * for the given instance
	 * 
	 * @param f GAIA feature you're converting
	 * @param fid ID of given feature
	 * @param fvalue GAIA feature value to use
	 * @param inst Weka Instance to add feature value to
	 * @param a Weka attribute to set the value for
	 * @param ispredict True if the feature is being added for prediction, not training.
	 * This value is used to increment training count, as well as to handle the
	 * target feature.
	 */
	private double gaiavalues2weka(Feature f, String fid, FeatureValue fvalue,
			Attribute a, boolean ispredict) {
		double value = 0;
		if(f instanceof NumFeature){
			NumValue v = (NumValue) fvalue;
			value = v.getNumber();
		} else if(f instanceof CategFeature){
			CategFeature cf = (CategFeature) f;
			String category = null;

			if(ispredict && this.targetfeatureid.equals(fid)) {
				// Testing set may not include the information for target attributes
				// In that case, just arbitrarily return the first item.
				// This is fine since we're not using Weka's evaluation system
				// where this value is needed.
				category = cf.getAllCategories().get(0);
			} else if(!(fvalue instanceof CategValue) && !ispredict && this.targetfeatureid.equals(fid)) {
				throw new InvalidStateException("All training instances must be labeled");
			} else if(fvalue.equals(FeatureValue.UNKNOWN_VALUE)) {
				value = Instance.missingValue();
				return value;
			} else {				
				category = ((CategValue) fvalue).getCategory();
			}
			
			// Increment training count
			if(this.fclasscount!= null && !ispredict) {
				this.fclasscount.increment(category);
			}
			
			// Handle weka instances
			value = a.indexOfValue(category);
		} else if(fvalue.equals(FeatureValue.UNKNOWN_VALUE)) {
			value = Instance.missingValue();
		} else {
			throw new UnsupportedTypeException("Unsupported feature type: "
					+fid+" is "
					+f.getClass().getCanonicalName());
		}
		
		return value;
	}
	
	/**
	 * Save instances using the weka CSV format
	 * 
	 * @param file CSV filename to write to.  If specified, the file will be save
	 * to <file>-<counter> where counter is the first integer that makes the file unique.
	 * @param instances Instances to save
	 */
	private void saveWekaInstances(String file, Instances instances){
		try {
			// Don't overwrite file.  If a file with the given name exists, make a new version.
			if(FileIO.fileExists(file)) {
				int counter = 1;
				while(FileIO.fileExists(file+"-"+counter)) {
					counter++;
				}

				file = file+"-"+counter;
			}

			Log.INFO("Saving instances in: "+file);

			CSVSaver saver = new CSVSaver();
			saver.setInstances(instances);
			saver.setFile(new File(file));
			saver.writeBatch();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void predict(Iterable<? extends Decorable> testitems) {
		int size = 0;
		for(Decorable d: testitems) {
			CategValue cv = this.predictSingleItem(d, false);
			d.setFeatureValue(this.targetfeatureid, cv);
			size++;
		}
		
		Instances testinstances = gaia2weka(testitems.iterator(), size, true);
		if(this.hasParameter("wekatestfile")){
			String savefile = this.getStringParameter("wekatestfile");
			this.saveWekaInstances(savefile, testinstances);
		}
	}
	
	@Override
	public FeatureValue predict(Decorable testitem) {
		boolean savewekatestfile = false;
		if(this.hasParameter("wekatestfile")){
			savewekatestfile = true;
		}
		
		return this.predictSingleItem(testitem, savewekatestfile);
	}
	
	/**
	 * Predict single item but specify whether or not to save weka
	 * test file for the single item.
	 * 
	 * @param testitem Single test item
	 * @param savewekatestfile True to save weka test file and false otherwise
	 * @return Predicted value
	 */
	private CategValue predictSingleItem(Decorable testitem, boolean savewekatestfile) {
		CategValue cvalue = null;
		Instances testinstances = gaia2weka(testitem, true);
		try {
			if(savewekatestfile){
				String savefile = this.getStringParameter("wekatestfile");
				this.saveWekaInstances(savefile+"-"+testitem, testinstances);
			}

			int numinstances = testinstances.numInstances();
			if(numinstances != 1) {
				throw new InvalidStateException("Only one predicted item should ever be returned");
			}
			
			Instance inst = testinstances.instance(0);
			double prob[] = this.wekaclassifier.distributionForInstance(inst);

			// Can just take maximum.  This is equivalent to what Weka does
			// to classify the instance.  This saves the cost of recomputing.
			int pred = ArrayUtils.maxValueIndex(prob);
			cvalue = new CategValue(this.targetcategories.get(pred), prob);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return cvalue;
	}
	
	@Override
	public void loadVBOC(String directory) {
		try {
			// Load configurations
			this.targetcategories =
				new UnmodifiableList<String>(Arrays.asList(this.getStringParameter("saved-targetcategories").split(",")));
			
			this.featureids.add(this.targetfeatureid);
			
			// Deserialize model
			ObjectInputStream ois = new ObjectInputStream(
					new FileInputStream(directory+File.separator+"saved.wekamodel"));
			this.wekaclassifier = (Classifier) ois.readObject();
			ois.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void saveVBOC(String directory) {
		try {
			// Save the local features
			this.setParameter("saved-targetcategories",
					IteratorUtils.iterator2string(this.targetcategories.iterator(), ","));
			
			// Overwrite default save feature ids since Weka Classifier
			// needs keeps the label in the feature ids list
			List<String> modfeatureids = new ArrayList<String>(featureids);
			modfeatureids.remove(this.targetfeatureid);
			this.setParameter("saved-featureids", ListUtils.list2string(modfeatureids, ","));
			
			// Serialize model
			FileIO.createDirectories(directory);
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(directory+File.separator+"saved.wekamodel"));
			oos.writeObject(this.wekaclassifier);
			oos.flush();
			oos.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public VBClassifier copyModel() {
		WekaClassifier vbc = new WekaClassifier();
		vbc.copyParameters(this);
		vbc.setCID(this.getCID());
		
		vbc.targetschemaid = this.targetschemaid;
		vbc.targetfeatureid = this.targetfeatureid;
		
		// Load features to use
		vbc.featureids = new ArrayList<String>(this.featureids);
		vbc.targetcategories = new UnmodifiableList<String>(this.targetcategories);
		try {
			vbc.wekaclassifier = Classifier.makeCopy(this.wekaclassifier);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return vbc;
	}
	
}
