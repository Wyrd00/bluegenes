describe("Main Search Feature Tests", function() {
  beforeEach(function() {
    cy.visit("/")
  });

  it("Search for keyword terms and add results to a list", function() {
    cy.get(".home .search").within(() => {
      cy.get("input[class=typeahead-search]").type("pax6")
        .get(".dropdown").should('have.class', 'open')
        .get(".show-all .list-group-item").should("have.class", "active").and("have.text","Show all results")
        .get(".quicksearch-result").its("length").should("be.gt", 0);
    });
  });
  
  it("Gives suggestion results when typing in search", function() {
    cy.get(".home .search").within(() => {
      cy.get("input[class=typeahead-search]").type("mal*");
      cy.get(".quicksearch-result").its('length').should("be.gt", 0);
    });
  });

  it("Opens the search page to show search results", function() {
    cy.get(".home .search").within(() => {
      cy.get("input[class=typeahead-search]").type("mal*{enter}");
    });
    cy.url().should("include", "/search");

    cy.get(".results").within(() => {
      cy.get(".result").should("have.length.of.at.least", 10)
    });
  });
})