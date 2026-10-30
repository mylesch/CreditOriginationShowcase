package com.cos.domain.model;

import com.cos.domain.enums.PartyRole;
import com.cos.domain.enums.PartyType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Party {
    private String partyId;
    private PartyType partyType;
    private PartyRole partyRole;
    private Boolean isPrimary;
    private Person person;
    private Company company;
    private List<Address> addresses;
    private List<Phone> phones;
    private List<Email> emails;
    private List<Identification> identifications;
    private List<BankAccount> bankAccounts;
    private List<BureauResponse> bureauResponses;
    private PartyDecisions decisions;
}
