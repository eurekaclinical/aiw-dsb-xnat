package edu.emory.cci.aiw.dsb.xnat;

import org.protempa.proposition.LocalUniqueId;

/**
 *
 * @author Andrew Post
 */
public class XNATLocalUniqueId implements LocalUniqueId {

    public XNATLocalUniqueId(String className, String id) {
    }
    
    @Override
    public String getId() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public int getNumericalId() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public LocalUniqueId clone() {
       try {
            return (XNATLocalUniqueId) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError("Never reached!");
        }
    }
    
}
