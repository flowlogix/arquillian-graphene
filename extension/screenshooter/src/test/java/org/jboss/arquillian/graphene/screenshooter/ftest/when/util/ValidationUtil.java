/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.graphene.screenshooter.ftest.when.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.arquillian.extension.recorder.When;
import org.junit.Assert;

/**
 * Util class used for checking screenshots for their presence there in the {@link Constants#SCREENSHOTS_DIRECTORY}
 * directory
 *
 * @author <a href="mailto:mjobanek@redhat.com">Matous Jobanek</a>
 */
public class ValidationUtil {



    public static void verifyScreenshotPresence(Class testClass, When... whenArray) {
        verifyScreenshotPresence(testClass, Constants.TEST_METHOD_NAME, whenArray);
    }

    public static void verifyScreenshotPresence(Class testClass, List<String> namesOfAllFiles, When... whenArray) {
        verifyScreenshotPresence(testClass, Constants.TEST_METHOD_NAME, namesOfAllFiles, whenArray);
    }

    public static void verifyScreenshotPresence(Class testClass, String methodName, When... whenArray) {
        List<String> namesOfFiles = new ArrayList<String>();
        for (When when : whenArray) {
            namesOfFiles.add(when.toString());
        }
        verifyScreenshotPresence(testClass, methodName, namesOfFiles, whenArray);
    }

    public static void verifyScreenshotPresence(Class testClass, String methodName, List<String> namesOfAllFiles,
        When... whenArray) {
        String screenshotTestDirectory =
            Constants.SCREENSHOTS_DIRECTORY + testClass.getName() + File.separator + methodName + File.separator;

        File[] screenshotsFiles = new File(screenshotTestDirectory).listFiles();
        if (screenshotsFiles == null) {
            screenshotsFiles = new File[] {};
        }

        checkFilesCount(namesOfAllFiles, screenshotsFiles);
        for (File screenshotFile : screenshotsFiles) {
            Assert.assertTrue(
                "The file: " + screenshotFile.getName() + " should NOT be there in the screenshots directory",
                namesOfAllFiles.contains(screenshotFile.getName().replace(".png", "")));
        }

        for (When when : whenArray) {
            String screenshotFilePath = screenshotTestDirectory + when + ".png";

            File screenshotFile = new File(screenshotFilePath);
            Assert.assertTrue("The screenshot " + screenshotFilePath + " should exist", screenshotFile.exists());
            Assert.assertTrue("The screenshot " + screenshotFilePath + " should be a file", screenshotFile.isFile());
            Assert.assertTrue("The size of the screenshot " + screenshotFilePath + " should not be 0",
                screenshotFile.length() > 0);
        }
    }

    private static void checkFilesCount(List<String> namesOfAllFiles, File[] screenshotsFiles) {
        if (namesOfAllFiles.size() != screenshotsFiles.length) {
            StringBuffer message = new StringBuffer("The count of expected files doesn't correspond to the reality. ");
            message.append("Expected count: " + namesOfAllFiles.size() + " actual: " + screenshotsFiles.length);
            message.append("\nSpecifically, the expected file name(s):\n");

            for (String name : namesOfAllFiles) {
                message.append(name + ".png\n");
            }
            message.append("but in the directory there are(is):\n");
            for (File f : screenshotsFiles) {
                message.append(f.getName() + "\n");
            }
            Assert.fail(message.toString());
        }
    }
}