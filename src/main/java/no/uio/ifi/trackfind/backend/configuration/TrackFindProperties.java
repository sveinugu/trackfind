package no.uio.ifi.trackfind.backend.configuration;

import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.TreeSet;

@Configuration
@PropertySource("classpath:trackfind.properties")
@ConfigurationProperties(prefix = "tf")
@Data
public class TrackFindProperties {

    private Metamodel metamodel;

    @Data
    public static class Metamodel {

        private @NotBlank String advancedSectionName;
        private @NotBlank String basicSectionName;
        private @NotBlank String levelsSeparator;
        private @NotBlank String idAttribute;
        private @NotBlank String advancedIdAttribute;
        private @NotBlank String browserAttribute;
        private @NotBlank String dataTypeAttribute;
        private @NotBlank String dataURLAttribute;
        private @NotBlank String dataSourceAttribute;
        private TreeSet<String> basicAttributes;

    }

}
