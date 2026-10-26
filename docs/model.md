# Knowledge graph

Generated from `src/main/resources/model.ttl` by `java -jar target/dms.jar --diagrams`. Do not edit by hand; regenerate after changing the model. Node labels show the sensitivity weight in parentheses. Solid arrows: `isPartOf` (part to whole) and `isMoreDetailedThan` (fine to coarse). Dotted arrows: `isA` (member to category).

## Personal data

### Age

```mermaid
graph LR
  Age["Age (2)"]
  DateOfBirth["DateOfBirth (3)"]
  DayOfBirth["DayOfBirth (1)"]
  ExactAge["ExactAge (2)"]
  IsAdult["IsAdult (1)"]
  MonthOfBirth["MonthOfBirth (1)"]
  YearOfBirth["YearOfBirth (2)"]
  DayOfBirth -->|isPartOf| DateOfBirth
  MonthOfBirth -->|isPartOf| DateOfBirth
  YearOfBirth -->|isPartOf| DateOfBirth
  DateOfBirth -->|isMoreDetailedThan| ExactAge
  DateOfBirth -.->|isA| Age
  ExactAge -->|isMoreDetailedThan| IsAdult
  ExactAge -.->|isA| Age
  IsAdult -.->|isA| Age
  classDef other stroke-dasharray: 4 4,opacity: 0.6
```

### Billing

```mermaid
graph LR
  Account["Account (4)"]
  AccountBalance["AccountBalance (4)"]
  AccountNumber["AccountNumber (3)"]
  Billing["Billing (3)"]
  AccountBalance -->|isPartOf| Account
  AccountNumber -->|isPartOf| Account
  Account -.->|isA| Billing
  classDef other stroke-dasharray: 4 4,opacity: 0.6
```

### Contact

```mermaid
graph LR
  Contact["Contact (2)"]
  Email["Email (2)"]
  FaxNumber["FaxNumber (1)"]
  TelephoneNumber["TelephoneNumber (2)"]
  Email -.->|isA| Contact
  FaxNumber -.->|isA| Contact
  TelephoneNumber -.->|isA| Contact
  classDef other stroke-dasharray: 4 4,opacity: 0.6
```

### Identity

```mermaid
graph LR
  Country["Country (1)"]:::other
  DateOfBirth["DateOfBirth (3)"]:::other
  FatherName["FatherName (2)"]
  FirstName["FirstName (1)"]
  FullName["FullName (2)"]
  IDCard["IDCard (4)"]
  IDNumber["IDNumber (3)"]
  Identity["Identity (3)"]
  LastName["LastName (1)"]
  MiddleName["MiddleName (1)"]
  MotherName["MotherName (2)"]
  Passport["Passport (4)"]
  PassportNumber["PassportNumber (3)"]
  PersonalSign["PersonalSign (3)"]
  FirstName -->|isPartOf| FullName
  LastName -->|isPartOf| FullName
  MiddleName -->|isPartOf| FullName
  FullName -.->|isA| Identity
  DateOfBirth -->|isPartOf| IDCard
  FatherName -->|isPartOf| IDCard
  FullName -->|isPartOf| IDCard
  IDNumber -->|isPartOf| IDCard
  MotherName -->|isPartOf| IDCard
  PersonalSign -->|isPartOf| IDCard
  IDCard -.->|isA| Identity
  Country -->|isPartOf| Passport
  DateOfBirth -->|isPartOf| Passport
  FullName -->|isPartOf| Passport
  PassportNumber -->|isPartOf| Passport
  Passport -.->|isA| Identity
  classDef other stroke-dasharray: 4 4,opacity: 0.6
```

### Location

```mermaid
graph LR
  City["City (2)"]
  Country["Country (1)"]
  Location["Location (2)"]
  StreetAddress["StreetAddress (3)"]
  StreetName["StreetName (2)"]
  StreetNumber["StreetNumber (1)"]
  City -->|isMoreDetailedThan| Country
  City -.->|isA| Location
  Country -.->|isA| Location
  StreetName -->|isPartOf| StreetAddress
  StreetNumber -->|isPartOf| StreetAddress
  StreetAddress -->|isMoreDetailedThan| City
  StreetAddress -.->|isA| Location
  classDef other stroke-dasharray: 4 4,opacity: 0.6
```

## Services

Each service is shown with its processing nodes (purpose in parentheses). Solid arrows: `requires`. Dashed arrows: `mayRequires`. Dotted arrows carry the condition under which a datum is required.

### AirportCheckIn

```mermaid
graph LR
  AirportCheckIn(["AirportCheckIn"])
  AirportCheckIn_IdentityVerification{{"IdentityVerification"}}
  AirportCheckIn --> AirportCheckIn_IdentityVerification
  AirportCheckIn_IdentityVerification -->|requires| FirstName
  AirportCheckIn_IdentityVerification -->|requires| LastName
  AirportCheckIn_IdentityVerification -->|requires| PassportNumber
  AirportCheckIn_IdentityVerification -.->|mayRequires| IDNumber
  FirstName["FirstName (1)"]
  IDNumber["IDNumber (3)"]
  LastName["LastName (1)"]
  PassportNumber["PassportNumber (3)"]
```

### CheckoutPay

```mermaid
graph LR
  CheckoutPay(["CheckoutPay"])
  CheckoutPay_Payment{{"Payment"}}
  CheckoutPay --> CheckoutPay_Payment
  CheckoutPay_Payment -->|requires| AccountBalance
  CheckoutPay_Payment -->|requires| AccountNumber
  AccountBalance["AccountBalance (4)"]
  AccountNumber["AccountNumber (3)"]
```

### CheckoutPayAdult

```mermaid
graph LR
  CheckoutPayAdult(["CheckoutPayAdult"])
  CheckoutPayAdult_Payment{{"Payment"}}
  CheckoutPayAdult --> CheckoutPayAdult_Payment
  CheckoutPayAdult_Payment -->|requires| AccountBalance
  CheckoutPayAdult_Payment -->|requires| AccountNumber
  CheckoutPayAdult_AgeVerification{{"AgeVerification"}}
  CheckoutPayAdult --> CheckoutPayAdult_AgeVerification
  CheckoutPayAdult_AgeVerification -->|requires| IsAdult
  AccountBalance["AccountBalance (4)"]
  AccountNumber["AccountNumber (3)"]
  IsAdult["IsAdult (1)"]
```

### EntryAllowance

```mermaid
graph LR
  EntryAllowance(["EntryAllowance"])
  EntryAllowance_AgeVerification{{"AgeVerification"}}
  EntryAllowance --> EntryAllowance_AgeVerification
  EntryAllowance_AgeVerification -->|requires| IsAdult
  IsAdult["IsAdult (1)"]
```

### LocalInfoFinder

```mermaid
graph LR
  LocalInfoFinder(["LocalInfoFinder"])
  LocalInfoFinder_LocalSearch{{"LocalSearch"}}
  LocalInfoFinder --> LocalInfoFinder_LocalSearch
  LocalInfoFinder_LocalSearch -->|requires| City
  City["City (2)"]
```

### MapProvision

```mermaid
graph LR
  MapProvision(["MapProvision"])
  MapProvision_LocalSearch{{"LocalSearch"}}
  MapProvision --> MapProvision_LocalSearch
  MapProvision_LocalSearch -->|requires| City
  City["City (2)"]
```

### OrderPlacement

```mermaid
graph LR
  OrderPlacement(["OrderPlacement"])
  OrderPlacement_OrderFulfilment{{"OrderFulfilment"}}
  OrderPlacement --> OrderPlacement_OrderFulfilment
  OrderPlacement_OrderFulfilment -->|requires| Email
  OrderPlacement_OrderFulfilment -->|requires| FirstName
  OrderPlacement_OrderFulfilment -->|requires| LastName
  OrderPlacement_Payment{{"Payment"}}
  OrderPlacement --> OrderPlacement_Payment
  OrderPlacement_Payment -.->|"requires when PaysByBankAccount"| AccountNumber
  AccountNumber["AccountNumber (3)"]
  Email["Email (2)"]
  FirstName["FirstName (1)"]
  LastName["LastName (1)"]
```

### PostalFormFilling

```mermaid
graph LR
  PostalFormFilling(["PostalFormFilling"])
  PostalFormFilling_Delivery{{"Delivery"}}
  PostalFormFilling --> PostalFormFilling_Delivery
  PostalFormFilling_Delivery -->|requires| City
  PostalFormFilling_Delivery -->|requires| StreetName
  PostalFormFilling_Delivery -->|requires| StreetNumber
  PostalFormFilling_Delivery -.->|"requires when InternationalDelivery"| TelephoneNumber
  PostalFormFilling_DeliveryNotification{{"DeliveryNotification"}}
  PostalFormFilling --> PostalFormFilling_DeliveryNotification
  PostalFormFilling_DeliveryNotification -.->|mayRequires| Email
  City["City (2)"]
  Email["Email (2)"]
  StreetName["StreetName (2)"]
  StreetNumber["StreetNumber (1)"]
  TelephoneNumber["TelephoneNumber (2)"]
```

### RestaurantFinder

```mermaid
graph LR
  RestaurantFinder(["RestaurantFinder"])
  RestaurantFinder_LocalSearch{{"LocalSearch"}}
  RestaurantFinder --> RestaurantFinder_LocalSearch
  RestaurantFinder_LocalSearch -->|requires| City
  RestaurantFinder_AgeVerification{{"AgeVerification"}}
  RestaurantFinder --> RestaurantFinder_AgeVerification
  RestaurantFinder_AgeVerification -.->|"requires when ContainsAlcohol"| IsAdult
  City["City (2)"]
  IsAdult["IsAdult (1)"]
```

### ShopFinder

```mermaid
graph LR
  ShopFinder(["ShopFinder"])
  ShopFinder_LocalSearch{{"LocalSearch"}}
  ShopFinder --> ShopFinder_LocalSearch
  ShopFinder_LocalSearch -->|requires| City
  City["City (2)"]
```

### SignInvoice

```mermaid
graph LR
  SignInvoice(["SignInvoice"])
  SignInvoice_ContractSigning{{"ContractSigning"}}
  SignInvoice --> SignInvoice_ContractSigning
  SignInvoice_ContractSigning -->|requires| PersonalSign
  SignInvoice_ContractSigning -.->|mayRequires| FirstName
  SignInvoice_ContractSigning -.->|mayRequires| LastName
  FirstName["FirstName (1)"]
  LastName["LastName (1)"]
  PersonalSign["PersonalSign (3)"]
```

### SubscriptionPay

```mermaid
graph LR
  SubscriptionPay(["SubscriptionPay"])
  SubscriptionPay_Payment{{"Payment"}}
  SubscriptionPay --> SubscriptionPay_Payment
  SubscriptionPay_Payment -->|requires| AccountBalance
  SubscriptionPay_Payment -->|requires| AccountNumber
  SubscriptionPay_PaymentReceipt{{"PaymentReceipt"}}
  SubscriptionPay --> SubscriptionPay_PaymentReceipt
  SubscriptionPay_PaymentReceipt -.->|mayRequires| Email
  AccountBalance["AccountBalance (4)"]
  AccountNumber["AccountNumber (3)"]
  Email["Email (2)"]
```

### SubscriptionSignUp

```mermaid
graph LR
  SubscriptionSignUp(["SubscriptionSignUp"])
  SubscriptionSignUp_AccountCreation{{"AccountCreation"}}
  SubscriptionSignUp --> SubscriptionSignUp_AccountCreation
  SubscriptionSignUp_AccountCreation -->|requires| Email
  SubscriptionSignUp_AccountCreation -->|requires| FirstName
  SubscriptionSignUp_AccountCreation -->|requires| LastName
  SubscriptionSignUp_CustomerSupport{{"CustomerSupport"}}
  SubscriptionSignUp --> SubscriptionSignUp_CustomerSupport
  SubscriptionSignUp_CustomerSupport -.->|mayRequires| TelephoneNumber
  Email["Email (2)"]
  FirstName["FirstName (1)"]
  LastName["LastName (1)"]
  TelephoneNumber["TelephoneNumber (2)"]
```

## DPV-PD mapping

| Data type | Sensitivity | DPV-PD concept |
|---|---|---|
| Account | 4 | `pd:BankAccount` |
| AccountBalance | 4 | - |
| AccountNumber | 3 | `pd:AccountIdentifier` |
| Age | 2 | `pd:Age` |
| Billing | 3 | `pd:Financial` |
| City | 2 | `pd:City` |
| Contact | 2 | `pd:Contact` |
| Country | 1 | `pd:Country` |
| DateOfBirth | 3 | `pd:BirthDate` |
| DayOfBirth | 1 | `pd:BirthDate` |
| Email | 2 | `pd:EmailAddress` |
| ExactAge | 2 | `pd:Age` |
| FatherName | 2 | - |
| FaxNumber | 1 | - |
| FirstName | 1 | `pd:Name` |
| FullName | 2 | `pd:Name` |
| IDCard | 4 | `pd:OfficialID` |
| IDNumber | 3 | `pd:OfficialID` |
| Identity | 3 | `pd:Identifying` |
| IsAdult | 1 | `pd:AgeRange` |
| LastName | 1 | `pd:Name` |
| Location | 2 | `pd:Location` |
| MiddleName | 1 | `pd:Name` |
| MonthOfBirth | 1 | `pd:BirthDate` |
| MotherName | 2 | - |
| Passport | 4 | `pd:Passport` |
| PassportNumber | 3 | `pd:Passport` |
| PersonalSign | 3 | - |
| StreetAddress | 3 | `pd:PhysicalAddress` |
| StreetName | 2 | `pd:Street` |
| StreetNumber | 1 | `pd:HouseNumber` |
| TelephoneNumber | 2 | `pd:TelephoneNumber` |
| YearOfBirth | 2 | `pd:BirthDate` |
