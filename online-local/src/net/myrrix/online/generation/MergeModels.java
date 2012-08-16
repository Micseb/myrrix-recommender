/*
 * Copyright Myrrix Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.myrrix.online.generation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;

import net.myrrix.common.collection.FastByIDMap;
import net.myrrix.online.factorizer.MatrixUtils;

/**
 * <p>Merges two model files into one model file. The models have to be "compatible" in order to make any sense,
 * in the sense that model 1 must map As to Bs and model 2, Bs to Cs, to make a model from As to Cs.</p>
 *
 * <p>The resulting model file can be plugged directly into another instance's working directory.</p>
 *
 * <p>Usage: MergeModels [model.bin file 1] [model.bin file 2] [merged model.bin]</p>
 *
 * <p>This is a simple utility class and an experiment which may be removed.</p>
 *
 * @author Sean Owen
 */
public final class MergeModels {

  private MergeModels() {
  }

  public static void main(String[] args) throws Exception {
    File model1File = new File(args[0]);
    File model2File = new File(args[1]);
    File mergedModelFile = new File(args[2]);
    merge(model1File, model2File, mergedModelFile);
  }

  public static void merge(File model1File, File model2File, File mergedModelFile) throws IOException {

    Generation model1 = readGeneration(model1File);
    Generation model2 = readGeneration(model2File);

    FastByIDMap<float[]> x1 = model1.getX();
    FastByIDMap<float[]> y1 = model1.getY();
    FastByIDMap<float[]> x2 = model2.getX();
    FastByIDMap<float[]> y2 = model2.getY();

    RealMatrix translation = multiply(y1, x2);

    FastByIDMap<float[]> xMerged = MatrixUtils.multiply(translation.transpose(), x1);

    FastIDSet emptySet = new FastIDSet();
    FastByIDMap<FastIDSet> knownItems = new FastByIDMap<FastIDSet>();
    LongPrimitiveIterator it = xMerged.keySetIterator();
    while (it.hasNext()) {
      knownItems.put(it.nextLong(), emptySet);
    }

    Generation merged = new Generation(knownItems, xMerged, y2);
    writeGeneration(merged, mergedModelFile);
  }

  private static Generation readGeneration(File f) throws IOException {
    ObjectInputStream in = new ObjectInputStream(new FileInputStream(f));
    try {
      GenerationSerializer serializer = (GenerationSerializer) in.readObject();
      return serializer.getGeneration();
    } catch (ClassNotFoundException cnfe) {
      throw new IllegalStateException(cnfe);
    } finally {
      in.close();
    }
  }

  private static void writeGeneration(Generation generation, File f) throws IOException {
    ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f));
    try {
      out.writeObject(new GenerationSerializer(generation));
    } finally {
      out.close();
    }
  }

  private static RealMatrix multiply(FastByIDMap<float[]> left, FastByIDMap<float[]> right) {
    int numRows = left.entrySet().iterator().next().getValue().length;
    int numCols = right.entrySet().iterator().next().getValue().length;
    double[][] translationData = new double[numRows][numCols];
    for (FastByIDMap.MapEntry<float[]> entry1 : left.entrySet()) {
      float[] leftCol = entry1.getValue();
      float[] rightRow = right.get(entry1.getKey());
      if (rightRow != null) {
        for (int row = 0; row < numRows; row++) {
          float leftColAtRow = leftCol[row];
          double[] translationDataAtRow = translationData[row];
          for (int col = 0; col < numCols; col++) {
            translationDataAtRow[col] += leftColAtRow * rightRow[col];
          }
        }
      }
    }
    return new Array2DRowRealMatrix(translationData);
  }

}
