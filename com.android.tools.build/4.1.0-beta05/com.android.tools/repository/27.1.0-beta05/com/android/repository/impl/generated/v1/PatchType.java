
package com.android.repository.impl.generated.v1;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import com.android.repository.impl.meta.TrimStringAdapter;


/**
 * DO NOT EDIT
 * This file was generated by xjc from repo-common-01.xsd. Any changes will be lost upon recompilation of the schema.
 * See the schema file for instructions on running xjc.
 *
 *
 *                 A zip file containing a binary diff between a previous version of this package
 *                 (specified in <base-on>) and the current version.
 *
 *
 * <p>Java class for patchType complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType name="patchType"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="based-on" type="{http://schemas.android.com/repository/android/common/01}revisionType"/&gt;
 *         &lt;group ref="{http://schemas.android.com/repository/android/common/01}archiveFields"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "patchType", propOrder = {
    "basedOn",
    "size",
    "checksum",
    "url"
})
@SuppressWarnings({
    "override",
    "unchecked"
})
public class PatchType
    extends com.android.repository.impl.meta.Archive.PatchType
{

    @XmlElement(name = "based-on", required = true)
    protected com.android.repository.impl.generated.v1.RevisionType basedOn;
    protected long size;
    @XmlElement(required = true)
    @XmlJavaTypeAdapter(TrimStringAdapter.class)
    protected String checksum;
    @XmlElement(required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlSchemaType(name = "token")
    protected String url;

    /**
     * Gets the value of the basedOn property.
     *
     * @return
     *     possible object is
     *     {@link com.android.repository.impl.generated.v1.RevisionType }
     *
     */
    public com.android.repository.impl.generated.v1.RevisionType getBasedOn() {
        return basedOn;
    }

    /**
     * Sets the value of the basedOn property.
     *
     * @param value
     *     allowed object is
     *     {@link com.android.repository.impl.generated.v1.RevisionType }
     *
     */
    public void setBasedOnInternal(com.android.repository.impl.generated.v1.RevisionType value) {
        this.basedOn = value;
    }

    /**
     * Gets the value of the size property.
     *
     */
    public long getSize() {
        return size;
    }

    /**
     * Sets the value of the size property.
     *
     */
    public void setSize(long value) {
        this.size = value;
    }

    /**
     * Gets the value of the checksum property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * Sets the value of the checksum property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setChecksum(String value) {
        this.checksum = value;
    }

    /**
     * Gets the value of the url property.
     *
     * @return
     *     possible object is
     *     {@link String }
     *
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the value of the url property.
     *
     * @param value
     *     allowed object is
     *     {@link String }
     *
     */
    public void setUrl(String value) {
        this.url = value;
    }

    public boolean isValidChecksum(String value) {
        return ((value != null)&&(value.matches("^([0-9a-fA-F]){40}$")));
    }

    public void setBasedOn(com.android.repository.impl.meta.RevisionType value) {
        setBasedOnInternal(((com.android.repository.impl.generated.v1.RevisionType) value));
    }

    public ObjectFactory createFactory() {
        return new ObjectFactory();
    }

}
