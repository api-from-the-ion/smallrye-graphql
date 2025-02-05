package io.smallrye.graphql.schema.creator.type;

import java.util.List;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.smallrye.graphql.schema.Annotations;
import io.smallrye.graphql.schema.helper.DeprecatedDirectivesHelper;
import io.smallrye.graphql.schema.helper.DescriptionHelper;
import io.smallrye.graphql.schema.helper.Direction;
import io.smallrye.graphql.schema.helper.Directives;
import io.smallrye.graphql.schema.helper.IgnoreHelper;
import io.smallrye.graphql.schema.helper.TypeAutoNameStrategy;
import io.smallrye.graphql.schema.helper.TypeNameHelper;
import io.smallrye.graphql.schema.model.DirectiveInstance;
import io.smallrye.graphql.schema.model.EnumType;
import io.smallrye.graphql.schema.model.EnumValue;
import io.smallrye.graphql.schema.model.Reference;
import io.smallrye.graphql.schema.model.ReferenceType;

/**
 * This create an Enum Type.
 *
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public class EnumCreator implements Creator<EnumType> {
    private static final Logger LOG = Logger.getLogger(EnumCreator.class.getName());

    private final TypeAutoNameStrategy autoNameStrategy;
    private Directives directives;

    private final DeprecatedDirectivesHelper deprecatedHelper;

    public EnumCreator(TypeAutoNameStrategy autoNameStrategy) {
        this.autoNameStrategy = autoNameStrategy;
        this.deprecatedHelper = new DeprecatedDirectivesHelper();
    }

    public void setDirectives(Directives directives) {
        this.directives = directives;
    }

    @Override
    public EnumType create(ClassInfo classInfo, Reference reference) {
        LOG.debug("Creating enum from " + classInfo.name().toString());

        Annotations annotations = Annotations.getAnnotationsForClass(classInfo);

        // Name
        String name = TypeNameHelper.getAnyTypeName(classInfo,
                annotations,
                autoNameStrategy,
                ReferenceType.ENUM,
                reference.getClassParametrizedTypes());

        // Description
        Optional<String> maybeDescription = DescriptionHelper.getDescriptionForType(annotations);

        EnumType enumType = new EnumType(classInfo.name().toString(), name, maybeDescription.orElse(null));

        // Directives
        enumType.setDirectiveInstances(getDirectiveInstances(annotations, enumType.getClassName(), false));

        // Values
        List<FieldInfo> fields = classInfo.fields();
        for (FieldInfo field : fields) {
            if (classInfo.name().equals(field.type().name())) { // Only include the enum fields
                Annotations annotationsForField = Annotations.getAnnotationsForPojo(Direction.OUT, field);
                if (!field.type().kind().equals(Type.Kind.ARRAY) && !IgnoreHelper.shouldIgnore(annotationsForField, field)) {
                    String description = annotationsForField.getOneOfTheseAnnotationsValue(Annotations.DESCRIPTION)
                            .orElse(null);
                    EnumValue enumValue = new EnumValue(description, field.name(),
                            getDirectiveInstances(annotationsForField, field.name(), true));
                    addDirectiveForDeprecated(annotationsForField, enumValue);
                    enumType.addValue(enumValue);

                }
            }
        }

        return enumType;
    }

    @Override
    public String getDirectiveLocation() {
        return "ENUM";
    }

    private List<DirectiveInstance> getDirectiveInstances(Annotations annotations, String referenceName, boolean enumValue) {
        // enumValue for switching if to filter out ENUM_VALUE directives or ENUM
        return directives.buildDirectiveInstances(annotations, enumValue ? "ENUM_VALUE" : getDirectiveLocation(),
                referenceName);
    }

    private void addDirectiveForDeprecated(Annotations annotationsForOperation, EnumValue enumValue) {
        if (deprecatedHelper != null && directives != null) {
            deprecatedHelper
                    .transformDeprecatedToDirective(annotationsForOperation,
                            directives.getDirectiveTypes().get(DotName.createSimple("io.smallrye.graphql.api.Deprecated")))
                    .ifPresent(deprecatedDirective -> {
                        LOG.debug(
                                "Adding deprecated directive " + deprecatedDirective + " to enum value '" + enumValue.getValue()
                                        + "'");
                        enumValue.addDirectiveInstance(deprecatedDirective);
                    });
        }
    }

}
