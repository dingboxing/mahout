/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.df.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.df.data.Dataset.Attribute;

public class DataLoaderTest extends TestCase {

  protected Random rng;

  @Override
  protected void setUp() throws Exception {
    RandomUtils.useTestSeed();
    rng = RandomUtils.getRandom();
  }

  public void testLoadDataWithDescriptor() throws Exception {
    int nbAttributes = 10;
    int datasize = 100;

    // prepare the descriptors
    String descriptor = Utils.randomDescriptor(rng, nbAttributes);
    Attribute[] attrs = DescriptorUtils.parseDescriptor(descriptor);

    // prepare the data
    double[][] data = Utils.randomDoubles(rng, descriptor, datasize);
    List<Integer> missings = new ArrayList<Integer>();
    String[] sData = prepareData(data, attrs, missings);
    Dataset dataset = DataLoader.generateDataset(descriptor, sData);
    Data loaded = DataLoader.loadData(dataset, sData);

    testLoadedData(data, attrs, missings, loaded);
    testLoadedDataset(data, attrs, missings, loaded);
  }

  /**
   * Test method for
   * {@link org.apache.mahout.df.data.DataLoader#generateDataset(java.lang.String, java.lang.String[])}.
   */
  public void testGenerateDataset() throws Exception {
    int nbAttributes = 10;
    int datasize = 100;

    // prepare the descriptors
    String descriptor = Utils.randomDescriptor(rng, nbAttributes);
    Attribute[] attrs = DescriptorUtils.parseDescriptor(descriptor);

    // prepare the data
    double[][] data = Utils.randomDoubles(rng, descriptor, datasize);
    List<Integer> missings = new ArrayList<Integer>();
    String[] sData = prepareData(data, attrs, missings);
    Dataset expected = DataLoader.generateDataset(descriptor, sData);

    Dataset dataset = DataLoader.generateDataset(descriptor, sData);
    
    assertEquals(expected, dataset);
  }

  /**
   * Converts the data to an array of comma-separated strings and adds some
   * missing values in all but IGNORED attributes
   * 
   * @param data
   * @param attrs
   * @param missings indexes of vectors with missing values
   * @return
   */
  protected String[] prepareData(double[][] data, Attribute[] attrs, List<Integer> missings) {
    int nbAttributes = attrs.length;

    String[] sData = new String[data.length];

    for (int index = 0; index < data.length; index++) {
      int missingAttr;
      if (rng.nextDouble() < 0.0) {
        // add a missing value
        missings.add(index);

        // choose a random attribute (not IGNORED)
        do {
          missingAttr = rng.nextInt(nbAttributes);
        } while (attrs[missingAttr].isIgnored());
      } else {
        missingAttr = -1;
      }

      StringBuilder builder = new StringBuilder();

      for (int attr = 0; attr < nbAttributes; attr++) {
        if (attr == missingAttr) {
          // add a missing value here
          builder.append('?').append(',');
        } else {
          builder.append(data[index][attr]).append(',');
        }
      }

      sData[index] = builder.toString();
    }

    return sData;
  }

  /**
   * Test if the loaded data matches the source data
   * 
   * @param data
   * @param attrs
   * @param missings indexes of instance with missing values
   * @param loaded
   */
  protected static void testLoadedData(double[][] data, Attribute[] attrs, List<Integer> missings, Data loaded) {
    int nbAttributes = attrs.length;

    // check the vectors
    assertEquals("number of instance", data.length - missings.size(), loaded .size());

    // make sure that the attributes are loaded correctly
    int lind = 0;
    for (int index = 0; index < data.length; index++) {
      if (missings.contains(index))
        continue; // this vector won't be loaded

      double[] vector = data[index];
      Instance instance = loaded.get(lind);

      // make sure the id is correct
      assertEquals(lind, instance.id);

      int aId = 0;
      for (int attr = 0; attr < nbAttributes; attr++) {
        if (attrs[attr].isIgnored())
          continue;

        if (attrs[attr].isNumerical()) {
          assertEquals(vector[attr], instance.get(aId++));
        } else if (attrs[attr].isCategorical()) {
          checkCategorical(data, missings, loaded, attr, aId, vector[attr],
              instance.get(aId));
          aId++;
        } else if (attrs[attr].isLabel()) {
          checkLabel(data, missings, loaded, attr, vector[attr]);
        }
      }
      
      lind++;
    }

  }
  
  /**
   * Test if the loaded dataset matches the source data
   * 
   * @param data
   * @param attrs
   * @param missings indexes of instance with missing values
   * @param loaded
   */
  protected static void testLoadedDataset(double[][] data, Attribute[] attrs, List<Integer> missings, Data loaded) {
    int nbAttributes = attrs.length;

    int iId = 0;
    for (int index = 0; index < data.length; index++) {
      if (missings.contains(index)) {
        continue;
      }
      
      Instance instance = loaded.get(iId++);

      int aId = 0;
      for (int attr = 0; attr < nbAttributes; attr++) {
        if (attrs[attr].isIgnored() || attrs[attr].isLabel())
          continue;

        assertEquals(attrs[attr].isNumerical(), loaded.getDataset().isNumerical(aId));
        
        if (attrs[attr].isCategorical()) {
          double nValue = instance.get(aId);
          String oValue = Double.toString(data[index][attr]);
          assertEquals((double) loaded.getDataset().valueOf(aId, oValue), nValue);
        }

        aId++;
      }
    }

  }

  public void testLoadDataFromFile() throws Exception {
    int nbAttributes = 10;
    int datasize = 100;

    // prepare the descriptors
    String descriptor = Utils.randomDescriptor(rng, nbAttributes);
    Attribute[] attrs = DescriptorUtils.parseDescriptor(descriptor);

    // prepare the data
    double[][] source = Utils.randomDoubles(rng, descriptor, datasize);
    List<Integer> missings = new ArrayList<Integer>();
    String[] sData = prepareData(source, attrs, missings);
    Dataset dataset = DataLoader.generateDataset(descriptor, sData);

    Path dataPath = Utils.writeDataToTestFile(sData);
    FileSystem fs = dataPath.getFileSystem(new Configuration());
    Data loaded = DataLoader.loadData(dataset, fs, dataPath);

    testLoadedData(source, attrs, missings, loaded);
  }

  /**
   * Test method for
   * {@link org.apache.mahout.df.data.DataLoader#generateDataset(java.lang.String, org.apache.hadoop.fs.FileSystem, org.apache.hadoop.fs.Path)}.
   */
  public void testGenerateDatasetFromFile() throws Exception {
    int nbAttributes = 10;
    int datasize = 100;

    // prepare the descriptors
    String descriptor = Utils.randomDescriptor(rng, nbAttributes);
    Attribute[] attrs = DescriptorUtils.parseDescriptor(descriptor);

    // prepare the data
    double[][] source = Utils.randomDoubles(rng, descriptor, datasize);
    List<Integer> missings = new ArrayList<Integer>();
    String[] sData = prepareData(source, attrs, missings);
    Dataset expected = DataLoader.generateDataset(descriptor, sData);

    Path path = Utils.writeDataToTestFile(sData);
    FileSystem fs = path.getFileSystem(new Configuration());
    
    Dataset dataset = DataLoader.generateDataset(descriptor, fs, path);
    
    assertEquals(expected, dataset);
  }

  /**
   * each time oValue appears in data for the attribute 'attr', the nValue must
   * appear in vectors for the same attribute.
   * 
   * @param source
   * @param loaded
   * @param attr attribute's index in source
   * @param aId attribute's index in loaded
   * @param oValue old value in source
   * @param nValue new value in loaded
   */
  protected static void checkCategorical(double[][] source, List<Integer> missings,
      Data loaded, int attr, int aId, double oValue, double nValue) {
    int lind = 0;

    for (int index = 0; index < source.length; index++) {
      if (missings.contains(index))
        continue;

      if (source[index][attr] == oValue) {
        assertEquals(nValue, loaded.get(lind).get(aId));
      } else {
        assertFalse(nValue == loaded.get(lind).get(aId));
      }

      lind++;
    }
  }

  /**
   * each time value appears in data as a label, its corresponding code must
   * appear in all the instances with the same label.
   * 
   * @param source
   * @param loaded
   * @param labelInd label's index in source
   * @param value source label's value
   */
  protected static void checkLabel(double[][] source, List<Integer> missings,
      Data loaded, int labelInd, double value) {
    // label's code that corresponds to the value
    int code = loaded.getDataset().labelCode(Double.toString(value));

    int lind = 0;

    for (int index = 0; index < source.length; index++) {
      if (missings.contains(index))
        continue;

      if (source[index][labelInd] == value) {
        assertEquals(code, loaded.get(lind).label);
      } else {
        assertFalse(code == loaded.get(lind).label);
      }

      lind++;
    }
  }
}
