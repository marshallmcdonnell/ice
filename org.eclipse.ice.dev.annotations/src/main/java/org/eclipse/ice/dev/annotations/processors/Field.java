package org.eclipse.ice.dev.annotations.processors;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

/**
 * Container for Field information, taken from DataField Annotations, in
 * simplified form for use by Velocity template.
 *
 * @author Daniel Bluhm
 */
@Data
@Builder
@JsonDeserialize(builder = Field.FieldBuilder.class)
public class Field {
	/**
	 * Name of the field.
	 */
	String name;

	/**
	 * String representation of the field's type.
	 */
	String type;

	/**
	 * The default value of this field.
	 */
	String defaultValue;

	/**
	 * Comment to add to the field declaration.
	 */
	String docString;

	/**
	 * Whether or not this field can be null.
	 *
	 * This value affects the kind of checks generated in IDataElement.matches().
	 */
	boolean nullable;

	/**
	 * Whether or not the type of this field is a primitive type.
	 *
	 * This value affects the kind of checks generated in IDataElement.matches().
	 * This is inferred from the Field's type.
	 */
	boolean primitive;

	/**
	 * Whether or not this field should be included in IDataElement.matches().
	 */
	@Builder.Default boolean match = true;

	/**
	 * Generate a getter for this field.
	 */
	@Builder.Default boolean getter = true;

	/**
	 * Generate a setter for this field.
	 */
	@Builder.Default boolean setter = true;

	/**
	 * Whether this field is considered a "default" or included in all
	 * DataElements.
	 */
	boolean defaultField;

	/**
	 * Whether this field should be searchable with PersistenceHandler.
	 */
	@Builder.Default boolean search = true;

	/**
	 * Whether this field should return only one from PersistenceHandler.
	 */
	boolean unique;

	/**
	 * A list of alternate names for this field.
	 */
	@Singular("alias") List<Field> aliases;

	/**
	 * A list of annotations to apply to this field.
	 */
	@Singular("annotation") List<String> annotations;

	/**
	 * Set of Modifiers (public, static, final, etc.) to apply to this field.
	 */
	@Builder.Default Set<String> modifiers = Set.of("protected");

	/**
	 * Get a class by name or return null if not found
	 * @param cls
	 * @return found class or null
	 */
	private static Class<?> getClassOrNull(String cls) {
		try {
			return ClassUtils.getClass(cls);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	/**
	 * Return this Fields name ready for use in a method.
	 * @return capitalized name
	 */
	@JsonIgnore
	public String getNameForMethod() {
		return StringUtils.capitalize(this.name);
	}

	/**
	 * Instruct Jackson how to deserialize fields.
	 */
	private interface FieldBuilderMeta {
		@JsonDeserialize(contentAs = Field.class) FieldBuilder aliases(Collection<? extends Field> aliases);
		@JsonDeserialize(contentAs = String.class) FieldBuilder annotations(Collection<? extends String> annotations);
		@JsonDeserialize(contentAs = String.class) FieldBuilder modifiers(Set<String> modifiers);
		@JsonAlias("fieldName") FieldBuilder name(String name);
	}

	/**
	 * Builder class for Field. This class must be a static inner class of Field in
	 * order to take advantage of Lombok's @Builder annotation. The methods defined
	 * here replace the defaults generated by Lombok.
	 */
	@JsonPOJOBuilder(withPrefix = "")
	public static class FieldBuilder implements FieldBuilderMeta {
		/**
		 * Format type as String.
		 * @param type the type to be formatted.
		 * @return this
		 */
		@JsonIgnore
		public FieldBuilder type(Class<?> type) {
			this.type = type.getName().toString();
			this.primitive = type.isPrimitive();
			return this;
		}

		/**
		 * Format type as a String from a TypeMirror.
		 * @param type
		 * @return this
		 */
		@JsonIgnore
		public FieldBuilder type(TypeMirror type) {
			this.type = type.toString();
			this.primitive = type.getKind().isPrimitive();
			return this;
		}

		/**
		 * Take a raw string value and pass through without manipulating for use as
		 * type.
		 * @param type
		 * @return this
		 */
		@JsonProperty
		@JsonAlias("fieldType")
		public FieldBuilder type(String type) {
			if (type == null) {
				return this;
			}
			Class<?> cls = getClassOrNull(type);
			if (cls == null) {
				cls = getClassOrNull("java.lang." + type);
			}
			if (cls != null) {
				this.type(cls);
			} else {
				this.type = type;
			}
			return this;
		}

		/**
		 * Format Modifiers as string.
		 * @param modifiers
		 * @return this
		 */
		@JsonIgnore
		public FieldBuilder modifiersToString(Set<Modifier> modifiers) {
			return this.modifiers(modifiers.stream()
				.map(modifier -> modifier.toString())
				.collect(Collectors.toSet()));
		}
	}
}
