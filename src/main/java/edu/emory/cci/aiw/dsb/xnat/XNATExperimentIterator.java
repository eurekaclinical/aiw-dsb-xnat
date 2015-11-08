package edu.emory.cci.aiw.dsb.xnat;

import edu.wustl.nrg.xnat.AbstractDemographicData;
import edu.wustl.nrg.xnat.DemographicData;
import edu.wustl.nrg.xnat.InvestigatorData;
import edu.wustl.nrg.xnat.SubjectAssessorData;
import edu.wustl.nrg.xnat.SubjectData;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.protempa.DataSourceReadException;
import org.protempa.DataStreamingEvent;
import org.protempa.DataStreamingEventIterator;
import org.protempa.backend.dsb.filter.Filter;
import org.protempa.proposition.Constant;
import org.protempa.proposition.DataSourceBackendId;
import org.protempa.proposition.Event;
import org.protempa.proposition.Proposition;
import org.protempa.proposition.UniqueId;
import org.protempa.proposition.interval.AbsoluteTimeIntervalFactory;
import org.protempa.proposition.interval.Interval;
import org.protempa.proposition.interval.IntervalFactory;
import org.protempa.proposition.value.AbsoluteTimeGranularity;
import org.protempa.proposition.value.BooleanValue;
import org.protempa.proposition.value.DateValue;
import org.protempa.proposition.value.NominalValue;
import org.protempa.proposition.value.NumberValue;
import org.protempa.proposition.value.ValueType;

/**
 *
 * @author Andrew Post
 */
public class XNATExperimentIterator implements DataStreamingEventIterator<Proposition> {

    private final Iterator<String> subjectIds;
    private final int port;
    private final String hostname;
    private final JAXBContext jc;
    private final Unmarshaller unmarshaller;
    private final Filter filters;
    private final HashSet<String> propIds;
    private final String project;
    private final String username;
    private final String password;
    private final String path;
    private final String scheme;
    private final String dsbId;
    private final Calendar cal;
    private final AbsoluteTimeIntervalFactory intervalFactory;

    XNATExperimentIterator(String dsbId, String project, Set<String> subjectIds, String scheme, String hostname, int port, String path, String username, String password, Set<String> propIds, Filter filters) throws DataSourceReadException {
        assert dsbId != null : "dsbId cannot be null";
        assert project != null : "project cannot be null";
        assert subjectIds != null : "subjectIds cannot be null";
        assert hostname != null : "hostname cannot be null";
        this.project = project;
        this.subjectIds = new HashSet<>(subjectIds).iterator();
        this.scheme = scheme;
        this.hostname = hostname;
        this.port = port;
        this.path = path;
        this.username = username;
        this.password = password;
        if (propIds != null) {
            this.propIds = new HashSet<>(propIds);
        } else {
            this.propIds = new HashSet<>();
        }
        this.filters = filters;
        try {
            this.jc = JAXBContext.newInstance("edu.wustl.nrg.xnat");
            this.unmarshaller = this.jc.createUnmarshaller();
        } catch (JAXBException ex) {
            throw new DataSourceReadException(ex);
        }
        this.dsbId = dsbId;
        this.cal = Calendar.getInstance();
        this.intervalFactory = new AbsoluteTimeIntervalFactory();
    }

    @Override
    public boolean hasNext() throws DataSourceReadException {
        return subjectIds.hasNext();
    }

    @Override
    public DataStreamingEvent<Proposition> next() throws DataSourceReadException {
        String subjectId = this.subjectIds.next();
        List<Proposition> data = new ArrayList<>();
        try {
            DefaultHttpClient http = new DefaultHttpClient();
            String userpass = username + ":" + password;
            String encoded = new String(Base64.getEncoder().encode(userpass.getBytes()));
            URI uri = new URI(this.scheme, null, this.hostname, this.port,
                    this.path + "data/archive/projects/" + this.project + "/subjects/" + subjectId,
                    "format=xml", null);
            HttpGet get = new HttpGet(uri);
            get.addHeader("Authorization", "Basic " + encoded);
            HttpResponse resp = http.execute(get);
            processResponse(resp, subjectId, data);
        } catch (URISyntaxException ex) {
            throw new DataSourceReadException("Invalid URL for retrieving subject list", ex);
        } catch (IOException ex) {
            throw new DataSourceReadException(ex);
        }

        return new DataStreamingEvent(subjectId, data);
    }

    private void processResponse(HttpResponse resp, String subjectId, List<Proposition> data) throws IOException, AssertionError, DataSourceReadException {
        try (InputStream contentIn = resp.getEntity().getContent()) {
            SubjectData subjectData = ((JAXBElement<SubjectData>) this.unmarshaller.unmarshal(contentIn)).getValue();
            AbstractDemographicData demographics = subjectData.getDemographics();
            Constant patient = newPatient(subjectData, subjectId);
            Constant patientDetails = newPatientDetails(subjectData, demographics, subjectId);
            patient.addReference("patientDetails", patientDetails.getUniqueId());
            data.add(patient);
            SubjectData.Experiments experiments = subjectData.getExperiments();
            for (SubjectAssessorData experiment : experiments.getExperiment()) {
                Event encounter = newEncounter(experiment, subjectId);
                data.add(encounter);
                patientDetails.addReference("encounters", encounter.getUniqueId());

                Constant provider = newInvestigatorEvent(experiment);
                data.add(provider);
                encounter.addReference("provider", provider.getUniqueId());

                Event subjectAssessor = newSubjectAssessorEvent(experiment);
                data.add(subjectAssessor);
                encounter.addReference("EK_XNAT", subjectAssessor.getUniqueId());
                
            }
            data.add(patientDetails);
        } catch (JAXBException ex) {
            throw new DataSourceReadException("Error parsing response from XNAT", ex);
        } catch (IntrospectionException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new AssertionError(ex);
        }
    }

    private Constant newInvestigatorEvent(SubjectAssessorData experiment) {
        InvestigatorData investigator = experiment.getInvestigator();
        if (investigator != null) {
            Constant provider = new Constant("Provider", generateUniqueId("Provider", investigator.getID()));
            String firstname = investigator.getFirstname();
            String lastname = investigator.getLastname();
            provider.setProperty("firstName", NominalValue.getInstance(firstname));
            provider.setProperty("lastName", NominalValue.getInstance(lastname));
            return provider;
        } else {
            return null;
        }
    }

    private Event newSubjectAssessorEvent(SubjectAssessorData experiment) throws IntrospectionException, InvocationTargetException, IllegalAccessException, IllegalArgumentException {
        this.cal.clear();
        String className = experiment.getClass().getName();
        Event event = new Event("XNAT:" + className, generateUniqueId(className, experiment.getID()));
        XMLGregorianCalendar date = experiment.getDate();
        XMLGregorianCalendar time = experiment.getTime();
        Duration duration = experiment.getDuration();
        this.cal.set(date.getYear(), date.getMonth(), date.getDay(), time.getHour(), time.getMinute(), time.getSecond());
        Date start = this.cal.getTime();
        Date finish;
        if (duration != null) {
            duration.addTo(this.cal);
            finish = this.cal.getTime();
        } else {
            finish = start;
        }
        event.setInterval(this.intervalFactory.getInstance(start, AbsoluteTimeGranularity.SECOND, finish, AbsoluteTimeGranularity.SECOND));
        BeanInfo beanInfo = Introspector.getBeanInfo(experiment.getClass());
        PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
        for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
            if (!propertyDescriptor.getReadMethod().getDeclaringClass().equals(Object.class)) {
                Method readMethod = propertyDescriptor.getReadMethod();
                Object value = readMethod.invoke(experiment);
                Class<?> propertyType = propertyDescriptor.getPropertyType();
                String propertyName = propertyDescriptor.getName();
                if (Number.class.isAssignableFrom(propertyType)) {
                    event.setProperty(propertyName, value != null ? ValueType.NUMBERVALUE.parse(value.toString()) : null);
                } else if (propertyType.equals(String.class)) {
                    event.setProperty(propertyName, value != null ? NominalValue.getInstance((String) value) : null);
                } else if (propertyType.equals(XMLGregorianCalendar.class)) {
                    event.setProperty(propertyName, value != null ? DateValue.getInstance(((XMLGregorianCalendar) value).toGregorianCalendar().getTime()) : null);
                } else {
                    System.out.println("Don't know what to do: " + propertyDescriptor.getName() + "; type " + propertyType.getName());
                }
            }
        }
        return event;
    }

    private Event newEncounter(SubjectAssessorData assessorData, String subjectId) {
        Event encounter = new Event("Encounter", generateUniqueId("Encounter", assessorData.getID()));
        encounter.setProperty("encounterId", NominalValue.getInstance(subjectId));
        Float age = assessorData.getAge();
        if (age != null) {
            encounter.setProperty("age", NumberValue.getInstance(age.doubleValue()));
        }
        return encounter;
    }

    private Constant newPatient(SubjectData subjectData, String subjectId) {
        Constant patient = new Constant("Patient", generateUniqueId("Patient", subjectData.getID()));
        patient.setProperty("patientId", NominalValue.getInstance(subjectId));
        return patient;
    }

    private Constant newPatientDetails(SubjectData subjectData, AbstractDemographicData demographics, String subjectId) {
        Constant patientDetails = new Constant("PatientDetails", generateUniqueId("PatientDetails", subjectData.getID()));
        if (demographics instanceof DemographicData) {
            DemographicData dd = (DemographicData) demographics;
            patientDetails.setProperty("patientId", NominalValue.getInstance(subjectId));
            XMLGregorianCalendar dob = dd.getDob();
            if (dob != null) {
                patientDetails.setProperty("dateOfBirth", DateValue.getInstance(dob.toGregorianCalendar().getTime()));
            }
            BigInteger age = dd.getAge();
            if (age != null) {
                patientDetails.setProperty("ageInYears", NumberValue.getInstance(dd.getAge().intValue()));
            }
            patientDetails.setProperty("vitalStatus", BooleanValue.FALSE);
        }
        return patientDetails;
    }

    @Override
    public void close() throws DataSourceReadException {
    }

    protected final UniqueId generateUniqueId(String name, String id) {
        return new UniqueId(
                DataSourceBackendId.getInstance(this.dsbId),
                new XNATLocalUniqueId(name, id));
    }

}
