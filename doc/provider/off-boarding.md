# Provider Offboarding Guide

## Disable Provider

The following command will disable a provider and all its nodes:

`pmc provider disable --pubkey <provide_pubkey>`

Note: System Providers can propose disabling System Providers, Node Providers and Dapp Providers. Node Providers can enable/disable Dapp Providers without voting.

## Verification

After proposal was accepted you can check that provider is deactivated:

`pmc provider info --pubkey <provide_pubkey>`

