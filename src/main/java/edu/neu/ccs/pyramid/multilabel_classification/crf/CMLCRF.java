package edu.neu.ccs.pyramid.multilabel_classification.crf;

import edu.neu.ccs.pyramid.clustering.bmm.BMM;
import edu.neu.ccs.pyramid.dataset.LabelTranslator;
import edu.neu.ccs.pyramid.dataset.MultiLabel;
import edu.neu.ccs.pyramid.dataset.MultiLabelClfDataSet;
import edu.neu.ccs.pyramid.feature.FeatureList;
import edu.neu.ccs.pyramid.multilabel_classification.MultiLabelClassifier;
import edu.neu.ccs.pyramid.multilabel_classification.MultiLabelSuggester;
import edu.neu.ccs.pyramid.util.MathUtil;
import org.apache.mahout.math.Vector;

import java.io.*;
import java.util.List;

import static edu.neu.ccs.pyramid.dataset.DataSetUtil.gatherMultiLabels;

/**
 * Created by Rainicy on 12/12/15.
 */
public class CMLCRF implements MultiLabelClassifier, Serializable {
    private static final long serialVersionUID = 2L;
    /**
     * Y_1, Y_2,...,Y_L
     */
    private int numClasses;
    /**
     * X feature length
     */
    private int numFeatures;

    private Weights weights;

    private List<MultiLabel> supportedCombinations;

    private int numSupported;

    BMM bmm;

    public CMLCRF(MultiLabelClfDataSet dataSet, int numClusters) {
        this(dataSet.getNumClasses(), dataSet.getNumFeatures());
        this.setSupportedCombinations(gatherMultiLabels(dataSet));
        this.numSupported = supportedCombinations.size();
        System.out.println("supported vector: " + supportedCombinations);
        System.out.println("length of supported: " + this.numSupported);
        System.out.println("fitting bmm");
        this.bmm = new MultiLabelSuggester(dataSet,numClusters).getBmm();
        System.out.println("bmm done");
    }

    //todo remove this constructor
    public CMLCRF(int numClasses, int numFeatures) {
        this.numClasses = numClasses;
        this.numFeatures = numFeatures;
        this.weights = new Weights(numClasses, numFeatures);
    }


    public void setSupportedCombinations(List<MultiLabel> multiLabels) {
        this.supportedCombinations = multiLabels;
        this.numSupported = multiLabels.size();
    }


    /**
     * get the scores for all possible label combination
     * y and a given feature x.
     * @param vector
     * @return
     */
    public double[] predictCombinationScores(Vector vector){
        double[] classScores = predictClassScores(vector);
        double[] scores = new double[this.numSupported];
        for (int k=0;k<scores.length;k++){
            scores[k] = predictCombinationScore(vector, k, classScores);
        }
        return scores;
    }


    // for the feature-label pair
    private double predictClassScore(Vector vector, int classIndex){
        double score = 0.0;
        score += this.weights.getWeightsWithoutBiasForClass(classIndex).dot(vector);
        score += this.weights.getBiasForClass(classIndex);
        return score;
    }

    private double[] predictClassScores(Vector vector){
        double[] scores = new double[numClasses];
        for (int k=0;k<numClasses;k++){
            scores[k] = predictClassScore(vector, k);
        }
        return scores;
    }

    /**
     * get the score by a given feature x and given label combination.
     * @param vector
     * @param label
     * @return
     */
    public double predictCombinationScore(Vector vector, MultiLabel label, double[] classScores){
        double score = 0.0;
        for (int l: label.getMatchedLabels()){
            score += classScores[l];
        }

        score += computePureCombinationScore(label);

        return score;
    }


    /**
     * get the score by a given feature x and given label combination.
     * @param vector
     * @param label
     * @return
     */
    public double predictCombinationScore(Vector vector, MultiLabel label){
        double score = 0.0;
        for (int l=0; l<numClasses; l++) {
            if (label.matchClass(l)) {
                score += predictClassScore(vector,l);
            }
        }
        score += computePureCombinationScore(label);
        return score;
    }

    /**
     * the part of score which depends only on labels
     * @param label
     * @return
     */
    private double computePureCombinationScore(MultiLabel label){
        double score = 0;
        int pos = this.weights.getNumWeightsForFeatures();
        boolean[] matches = new boolean[numClasses];
        for (int match: label.getMatchedLabels()){
            matches[match] = true;
        }
        for (int l1=0; l1<numClasses; l1++) {
            for (int l2=l1+1; l2<numClasses; l2++) {
                if (!matches[l1] && !matches[l2]) {
                    score += this.weights.getWeightForIndex(pos);
                } else if (matches[l1] && !matches[l2]) {
                    score += this.weights.getWeightForIndex(pos + 1);
                } else if (!matches[l1] && matches[l2]) {
                    score += this.weights.getWeightForIndex(pos + 2);
                } else {
                    score += this.weights.getWeightForIndex(pos + 3);
                }
                pos += 4;
            }
        }

        score += bmm.logProbability(label.toVector(numClasses));
        return score;
    }

    /**
     *
     * get the score of a given feature x and given label
     * combination y_k.
     * @param vector
     * @param k
     * @return
     */
    public double predictCombinationScore(Vector vector, int k, double[] classScores){
        return predictCombinationScore(vector, supportedCombinations.get(k), classScores);
    }

    public double[] predictCombinationProbs(Vector vector){
        double[] scoreVector = this.predictCombinationScores(vector);
        double[] probVector = new double[this.numSupported];
        double logDenominator = MathUtil.logSumExp(scoreVector);
        for (int k=0;k<this.numSupported;k++){
            double logNumerator = scoreVector[k];
            double pro = Math.exp(logNumerator-logDenominator);
            probVector[k]=pro;
        }
        return probVector;
    }

    public double[] predictLogCombinationProbs(Vector vector){
        double[] scoreVector = this.predictCombinationScores(vector);
        double[] logProbVector = new double[this.numSupported];
        double logDenominator = MathUtil.logSumExp(scoreVector);
        for (int k=0;k<this.numSupported;k++) {
            double logNumerator = scoreVector[k];
            logProbVector[k]=logNumerator-logDenominator;
        }
        return logProbVector;
    }

    @Override
    public int getNumClasses() {
        return numClasses;
    }

    public int getNumSupported() {
        return numSupported;
    }

    public int getNumFeatures() {
        return numFeatures;
    }

    public Weights getWeights() {
        return weights;
    }

    public List<MultiLabel> getSupportedCombinations() {
        return supportedCombinations;
    }


    @Override
    public MultiLabel predict(Vector vector) {
        double[] scores = predictCombinationScores(vector);
        double maxScore = Double.NEGATIVE_INFINITY;
        int predictedCombination = 0;
        for (int k=0;k<scores.length;k++){
            double scoreCombinationK = scores[k];
            if (scoreCombinationK > maxScore){
                maxScore = scoreCombinationK;
                predictedCombination = k;
            }
        }
        return this.supportedCombinations.get(predictedCombination);
    }

    @Override
    public FeatureList getFeatureList() {
        return null;
    }

    @Override
    public LabelTranslator getLabelTranslator() {
        return null;
    }


    // TODO
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CMLCRF{");
        sb.append('}');
        return sb.toString();
    }

    public static CMLCRF deserialize(File file) throws Exception {
        try (
                FileInputStream fileInputStream = new FileInputStream(file);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                ObjectInputStream objectInputStream = new ObjectInputStream(bufferedInputStream);
        ){
            CMLCRF cmlcrf = (CMLCRF) objectInputStream.readObject();
            return cmlcrf;
        }
    }

    public static CMLCRF deserialize(String file) throws Exception {
        File file1 = new File(file);
        return deserialize(file1);
    }

    @Override
    public void serialize(File file) throws Exception {
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdir();
        }
        try (
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(bufferedOutputStream);
        ){
            objectOutputStream.writeObject(this);
        }
    }

    @Override
    public void serialize(String file) throws Exception {
        File file1 = new File(file);
        serialize(file1);
    }

}
