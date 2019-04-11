package no.uio.ifi.trackfind.backend.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import no.uio.ifi.trackfind.backend.operations.Operation;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Entity
@Table(name = "tf_versions")
@Data
@EqualsAndHashCode(of = {"id"})
@ToString
@NoArgsConstructor
public class TfVersion implements Serializable {

    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version", nullable = false)
    private String version;

    @Column(name = "operation", nullable = false)
    @Enumerated(EnumType.STRING)
    private Operation operation;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "time", nullable = false)
    private Date time;

}
