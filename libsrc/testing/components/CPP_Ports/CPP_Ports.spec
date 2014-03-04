# By default, the RPM will install to the standard REDHAWK SDR root location (/var/redhawk/sdr)
# You can override this at install time using --prefix /new/sdr/root when invoking rpm (preferred method, if you must)
%{!?_sdrroot: %define _sdrroot /var/redhawk/sdr}
%define _prefix %{_sdrroot}
Prefix: %{_prefix}

# Point install paths to locations within our target SDR root
%define _sysconfdir    %{_prefix}/etc
%define _localstatedir %{_prefix}/var
%define _mandir        %{_prefix}/man
%define _infodir       %{_prefix}/info

Name: CPP_Ports
Summary: Component %{name}
Version: 1.0.0
Release: 1
License: None
Group: REDHAWK/Components
Source: %{name}-%{version}.tar.gz
BuildRoot: %{_tmppath}/%{name}-root

Requires: redhawk >= 1.8
BuildRequires: redhawk >= 1.8
BuildRequires: autoconf automake libtool

# Interface requirements
Requires: bulkioInterfaces
BuildRequires: bulkioInterfaces

# C++ requirements
Requires: libomniORB4.1
Requires: boost >= 1.41
Requires: apache-log4cxx >= 0.10
BuildRequires: boost-devel >= 1.41
BuildRequires: libomniORB4.1-devel
BuildRequires: apache-log4cxx-devel >= 0.10

# Java requirements
Requires: java
BuildRequires: jdk

# Python requirements
Requires: python omniORBpy
BuildRequires: libomniORBpy3-devel
BuildRequires: python-devel >= 2.3

%description
Component %{name}

%prep
%setup

%build
# Implementation cpp
pushd cpp
./reconf
%define _bindir %{_prefix}/dom/components/CPP_Ports/cpp
%configure
make
popd

%install
rm -rf $RPM_BUILD_ROOT
# Implementation cpp
pushd cpp
%define _bindir %{_prefix}/dom/components/CPP_Ports/cpp
make install DESTDIR=$RPM_BUILD_ROOT
popd

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(-,redhawk,redhawk)
%dir %{_prefix}/dom/components/%{name}
%{_prefix}/dom/components/%{name}/CPP_Ports.scd.xml
%{_prefix}/dom/components/%{name}/CPP_Ports.prf.xml
%{_prefix}/dom/components/%{name}/CPP_Ports.spd.xml
%{_prefix}/dom/components/%{name}/cpp
%{_prefix}/dom/components/%{name}/python
%{_prefix}/dom/components/%{name}/java
%{_prefix}/dom/components/%{name}/

