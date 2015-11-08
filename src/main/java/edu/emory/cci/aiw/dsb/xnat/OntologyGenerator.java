/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.emory.cci.aiw.dsb.xnat;

import edu.wustl.nrg.xnat.ExperimentData;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;

/**
 *
 * @author arpost
 */
public class OntologyGenerator {

    private static final String PADDING = "        ";
    private static final String PADDING_WITH_COLUMN = "   |    ";
    private static final String PADDING_WITH_ENTRY = "   |--- ";
    private static final String BASE_CLASS = Object.class.getName();

    private Map<String, List<String>> entries;
    private boolean[] moreToCome;

    public static void main(final String[] args) {
        Reflections reflections = new Reflections("edu");
        Set<Class<? extends ExperimentData>> subTypesOf = reflections.getSubTypesOf(ExperimentData.class);
        List<Class<?>> leaves = new ArrayList<>();
        for (Class<? extends ExperimentData> clz : subTypesOf) {
            if (reflections.getSubTypesOf(clz).isEmpty()) {
                leaves.add(clz);
            }
        }
        new OntologyGenerator().printHierarchy(leaves);
    }

    public void printHierarchy(List<Class<?>> clazzes) {
        // clean values
        entries = new TreeMap<>();
        moreToCome = new boolean[99];

        // get all entries of tree
        traverseClasses(clazzes);

        // print collected entries as ASCII tree
        printHierarchy(BASE_CLASS, 0, Collections.EMPTY_SET);
    }

    private void printHierarchy(String node, int level, Set<String> parentProperties) {
        for (int i = 1; i < level; i++) {
            System.out.print(moreToCome[i - 1] ? PADDING_WITH_COLUMN : PADDING);
        }

        if (level > 0) {
            System.out.print(PADDING_WITH_ENTRY);
        }
        
        try {
            Class<?> forName = Class.forName(node);
            BeanInfo beanInfo = Introspector.getBeanInfo(forName);
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
            Set<String> propertyNames = new HashSet<>();
            for (PropertyDescriptor pd : propertyDescriptors) {
                propertyNames.add(pd.getName());
            }
            Set<String> newPropertyNames = new HashSet<>();
            for (PropertyDescriptor pd : propertyDescriptors) {
                String name = pd.getName();
                if (!parentProperties.contains(name)) {
                    newPropertyNames.add(name);
                }
            }
            System.out.println(node + " (" + StringUtils.join(newPropertyNames, ", ") + ")");
            if (entries.containsKey(node)) {
            final List<String> list = entries.get(node);

            for (int i = 0; i < list.size(); i++) {
                moreToCome[level] = (i < list.size() - 1);
                printHierarchy(list.get(i), level + 1, propertyNames);
            }
        }
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(OntologyGenerator.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IntrospectionException ex) {
            Logger.getLogger(OntologyGenerator.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
    }

    private void traverseClasses(List<Class<?>> clazzes) {
        for (Class<?> clz : clazzes) {
            traverseClasses(clz, 0);
        }
    }

    private void traverseClasses(final Class<?> clazz, final int level) {
        final Class<?> superClazz = clazz.getSuperclass();

        if (superClazz == null) {
            return;
        }

        final String name = clazz.getName();
        final String superName = superClazz.getName();

        if (entries.containsKey(superName)) {
            final List<String> list = entries.get(superName);

            if (!list.contains(name)) {
                list.add(name);
                Collections.sort(list); // SortedList
            }
        } else {
            entries.put(superName, new ArrayList<>(Arrays.asList(name)));
        }

        traverseClasses(superClazz, level + 1);
    }
}
