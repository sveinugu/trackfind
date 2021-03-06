package no.uio.ifi.inttests

import io.restassured.RestAssured
import io.restassured.RestAssured.given
import io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath
import org.apache.http.HttpStatus
import org.hamcrest.core.IsCollectionContaining.hasItems
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

const val EXAMPLE = "Example"

class Tests {

    @BeforeTest
    fun setup() {
        RestAssured.baseURI = "http://localhost"
        RestAssured.basePath = "/api/v1"
        RestAssured.port = 80
    }

    @Test
    fun testGetRepositories() {
        given().`when`().get("/repositories").then().statusCode(HttpStatus.SC_OK).assertThat()
            .body("", hasItems(EXAMPLE))
    }

    @Test
    fun testGetHubs() {
        given().`when`().get("/hubs/$EXAMPLE").then().statusCode(HttpStatus.SC_OK).assertThat()
            .body("", hasItems(EXAMPLE))
    }

    @Test
    fun testGetMetamodel() {
        given().`when`().get("/metamodel/$EXAMPLE/$EXAMPLE").then().statusCode(HttpStatus.SC_OK).assertThat()
            .body(
                matchesJsonSchemaInClasspath("metamodel.schema.json")
            )
    }

    @Test
    fun testGetCategories() {
        given().`when`().get("/categories/$EXAMPLE/$EXAMPLE").then().statusCode(HttpStatus.SC_OK).assertThat()
            .body("", hasItems("tracks", "experiments", "samples", "studies", "non_standard_samples"))
    }

    @Test
    fun testGetAttributes() {
        given().`when`().get("/attributes/$EXAMPLE/$EXAMPLE/samples").then().statusCode(HttpStatus.SC_OK).assertThat()
            .body(
                "",
                hasItems(
                    "phenotype",
                    "local_id",
                    "global_id",
                    "sample_type",
                    "biospecimen_class"
                )
            )
    }

    @Test
    fun testGetSubAttributes() {
        given().`when`().get("/attributes/$EXAMPLE/$EXAMPLE/samples?path=sample_type")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .assertThat()
            .body(
                "",
                hasItems(
                    "summary",
                    "cell_line",
                    "organism_part"
                )
            )
    }

    @Test
    fun testGetValues() {
        given().`when`().get("/values/$EXAMPLE/$EXAMPLE/samples?path=sample_type->cell_line->term_label")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .assertThat()
            .body(
                "",
                hasItems(
                    "H1-hESC"
                )
            )
    }

    @Test
    fun testGetValuesWithFilter() {
        given().`when`().get("/values/$EXAMPLE/$EXAMPLE/tracks?path=file_format->term_label&filter=ENCODE")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .assertThat()
            .body(
                "",
                hasItems(
                    "ENCODE narrow peak format"
                )
            )
    }

    @Test
    fun testSearchJSON() {
        given().`when`().get("/search/$EXAMPLE/$EXAMPLE?query=samples.content->'sample_type'->'cell_line'->'term_label' ? 'H1-hESC'")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .assertThat()
            .body(
                matchesJsonSchemaInClasspath("search.schema.json")
            )
    }

    @Test
    fun testSearchGSuite() {
        val actual = given()
            .headers(
                mapOf(
                    "Accept" to "text/plain"
                )
            )
            .`when`().get("/search/$EXAMPLE/$EXAMPLE?query=samples.content->'sample_type'->'cell_line'->'term_label' ? 'H1-hESC'")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().asString()
        val expected = javaClass.getResource("/search.sample.gsuite").readText()
        assertEquals(expected, actual)
    }

}
