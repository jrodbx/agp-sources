/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Main application object to encapsulate state, elements, and logic.
 */
const App = {
    init() {
        // fullReport is consumed from generated report-data.js
        if (typeof fullReport !== 'undefined') {
            CoverageReportApp.init(fullReport);
        } else {
            console.error("fullReport data not found. Ensure report-data.js is loaded.");
        }

        // Initialize SourceViewApp (no data needed initially)
        SourceViewApp.init();

        document.body.addEventListener('click', (e) => {
            if (e.target.closest('.class-link')) {
                e.preventDefault();
                const link = e.target.closest('.class-link');

                const moduleName = link.dataset.moduleName;
                const packageName = link.dataset.packageName;
                const className = link.dataset.className;

                const classObj = this.findClassObject(moduleName, packageName, className);

                if (classObj) {
                    const context = {
                        moduleName: moduleName,
                        testSuiteName: CoverageReportApp.state.filters.testSuite
                    };
                    this.showSourceView(classObj, context);
                } else {
                    console.error("Class data not found for source view.");
                }
            }
        });
    },

    findClassObject(moduleName, packageName, className) {
        if (typeof fullReport === 'undefined') return null;

        const module = fullReport.modules.find(m => m.name === moduleName);
        if (!module) return null;

        const pkg = (module.packages || []).find(p => p.name === packageName);
        if (!pkg) return null;

        return (pkg.classes || []).find(c => c.name === className);
    },

    showSourceView(classData, context) {
        document.getElementById('report-view').style.display = 'none';
        document.getElementById('source-view').style.display = 'block';
        document.getElementById('report-view-controls').classList.add('hidden');
        document.getElementById('source-view-controls').classList.remove('hidden');

        SourceViewApp.loadAndRender(classData, context);
    },

    showReportView() {
        document.getElementById('source-view').style.display = 'none';
        document.getElementById('report-view').style.display = 'block';
        document.getElementById('source-view-controls').classList.add('hidden');
        document.getElementById('report-view-controls').classList.remove('hidden');
    }
}

const CoverageReportApp = {
    state: {
        viewMode: 'flat', // 'flat' or 'tree'
        currentView: 'modules', // 'modules', 'packages', 'classes'
        selectedModule: null,
        selectedPackage: null,
        filters: { module: 'all', testSuite: 'Aggregated', package: 'all', class: 'all', variants: [], search: '' },
        sort: { by: 'name', order: 'asc' },
    },
    elements: {},
    fullReport: null,
    allPackages: [],
    allClasses: [],

    init(fullReport) {
        this.fullReport = fullReport;
        this.cacheDOMElements();
        this.populateHeaderInfo();
        this.populateGlobalStats();
        this.populateFilters();
        this.bindEvents();
        this.render();
        this.closeDropdownOnClickOutside();
    },

    cacheDOMElements() {
        this.elements = {
            headerTitle: document.querySelector('.header-title'),
            headerDate: document.querySelector('.header-date'),
            testSuiteFilterBtn: document.getElementById('testsuite-filter-btn'),
            testSuiteFilterText: document.getElementById('testsuite-filter-text'),
            testSuiteFilterDropdown: document.getElementById('testsuite-filter-dropdown'),
            testSuiteFilterList: document.getElementById('testsuite-filter-list'),
            moduleFilterBtn: document.getElementById('module-filter-btn'),
            moduleFilterText: document.getElementById('module-filter-text'),
            moduleFilterDropdown: document.getElementById('module-filter-dropdown'),
            moduleFilterList: document.getElementById('module-filter-list'),
            packageFilterBtn: document.getElementById('package-filter-btn'),
            packageFilterText: document.getElementById('package-filter-text'),
            packageFilterDropdown: document.getElementById('package-filter-dropdown'),
            packageFilterList: document.getElementById('package-filter-list'),
            classFilterBtn: document.getElementById('class-filter-btn'),
            classFilterText: document.getElementById('class-filter-text'),
            classFilterDropdown: document.getElementById('class-filter-dropdown'),
            classFilterList: document.getElementById('class-filter-list'),
            variantFilterBtn: document.getElementById('variant-filter-btn'),
            variantFilterText: document.getElementById('variant-filter-text'),
            variantFilterDropdown: document.getElementById('variant-filter-dropdown'),
            variantFilterList: document.getElementById('variant-filter-list'),
            viewModeBtn: document.getElementById('view-mode-btn'),
            viewModeText: document.getElementById('view-mode-text'),
            viewModeDropdown: document.getElementById('view-mode-dropdown'),
            viewModeList: document.getElementById('view-mode-list'),
            searchInput: document.getElementById('search-input'),
            searchClearBtn: document.getElementById('search-clear-btn'),
            viewToggles: document.getElementById('view-toggles'),
            flatBreadcrumbs: document.getElementById('flat-breadcrumbs'),
            flatViewControls: document.getElementById('flat-view-controls'),
            tableHeaders: document.getElementById('table-headers'),
            coverageData: document.getElementById('coverage-data'),
            totalModules: document.getElementById('total-modules'),
            totalClasses: document.getElementById('total-classes'),
            totalTests: document.getElementById('total-tests'),
        };
    },

    toggleDropdown(dropdownToToggle) {
        const allDropdowns = [
            this.elements.moduleFilterDropdown,
            this.elements.testSuiteFilterDropdown,
            this.elements.packageFilterDropdown,
            this.elements.classFilterDropdown,
            this.elements.variantFilterDropdown,
            this.elements.viewModeDropdown
        ];

        allDropdowns.forEach(dropdown => {
            if (dropdown !== dropdownToToggle) {
                dropdown.classList.add('hidden');
            }
        });

        dropdownToToggle.classList.toggle('hidden');
    },

    populateHeaderInfo() {
        if(this.fullReport.name) this.elements.headerTitle.textContent = this.fullReport.name;
        if(this.fullReport.timeStamp) this.elements.headerDate.textContent = this.fullReport.timeStamp;
    },

    populateGlobalStats() {
        if (this.elements.totalTests) {
            this.elements.totalTests.textContent = this.fullReport.numberOfTestsSuites || 0;
        }
    },

    populateFilters() {
        this.allPackages = this.fullReport.modules.flatMap(m => (m.packages || []).map(p => ({ name: p.name, moduleName: m.name })) );
        this.allClasses = this.fullReport.modules.flatMap(m =>
            (m.packages || []).flatMap(p =>
                (p.classes || []).map(c => ({ name: c.name, packageName: p.name, moduleName: m.name }))
            )
        );

        // Variants
        let allVariants = [];
        if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariants = this.fullReport.variantCoverages.map(v => v.name);
        }

        this.state.filters.variants = [...allVariants];

        const variantOptions = [{name: 'All', value: 'all'}, ...allVariants.map(v => ({name: v, value: v}))];

        this.elements.variantFilterList.innerHTML = variantOptions.map(opt => `
            <label class="flex items-center gap-2 cursor-pointer px-2 py-1.5 hover:bg-gray-100 rounded">
                <input type="checkbox" class="variant-toggle w-4 h-4 text-blue-600 rounded focus:ring-2 focus:ring-blue-500" data-variant="${opt.value}" ${this.state.filters.variants.includes(opt.value) || (this.state.filters.variants.length === allVariants.length && opt.value === 'all') ? 'checked' : ''} />
                <span class="text-sm text-gray-700">${opt.name}</span>
            </label>
        `).join('');

        // View Mode
        const viewOptions = [
            {name: 'Flat', value: 'flat'},
            {name: 'Tree', value: 'tree'},
        ];
        this.elements.viewModeList.innerHTML = viewOptions.map(opt =>
            `<a href="#" data-value="${opt.value}" class="dropdown-item">${opt.name}</a>`
        ).join('');
    },

    bindEvents() {
        this.elements.searchInput.addEventListener('input', () => {
            this.state.filters.search = this.elements.searchInput.value.trim().toLowerCase();
            this.render();
        });

        const dropdownConfigs = [
            { btn: this.elements.moduleFilterBtn, dropdown: this.elements.moduleFilterDropdown },
            { btn: this.elements.testSuiteFilterBtn, dropdown: this.elements.testSuiteFilterDropdown },
            { btn: this.elements.packageFilterBtn, dropdown: this.elements.packageFilterDropdown },
            { btn: this.elements.classFilterBtn, dropdown: this.elements.classFilterDropdown },
            { btn: this.elements.variantFilterBtn, dropdown: this.elements.variantFilterDropdown },
            { btn: this.elements.viewModeBtn, dropdown: this.elements.viewModeDropdown }
        ];

        dropdownConfigs.forEach(({ btn, dropdown }) => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleDropdown(dropdown);
            });
        });

        this.elements.flatViewControls.addEventListener('click', this.handleViewToggle.bind(this));
        this.elements.coverageData.addEventListener('click', this.handleRowClick.bind(this));
        this.elements.tableHeaders.addEventListener('click', this.handleHeaderClick.bind(this));
        this.elements.flatBreadcrumbs.addEventListener('click', this.handleBreadcrumbClick.bind(this));

        this.elements.testSuiteFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'testSuite'));
        this.elements.moduleFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'module'));
        this.elements.packageFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'package'));
        this.elements.classFilterList.addEventListener('click', this.handleFilterSelection.bind(this, 'class'));
        this.elements.variantFilterList.addEventListener('change', this.handleVariantSelection.bind(this));
        this.elements.viewModeList.addEventListener('click', this.handleViewModeChange.bind(this));
        this.elements.searchInput.addEventListener('input', () => {
            const searchTerm = this.elements.searchInput.value.trim().toLowerCase();
            this.state.filters.search = searchTerm;
            if (searchTerm.length > 0) {
                this.elements.searchClearBtn.classList.remove('hidden');
            } else {
                this.elements.searchClearBtn.classList.add('hidden');
            }
            this.render();
        });
        this.elements.searchClearBtn.addEventListener('click', () => {
            this.elements.searchInput.value = '';
            this.state.filters.search = '';
            this.elements.searchClearBtn.classList.add('hidden');
            this.render();
            this.elements.searchInput.focus();
        });
    },

    handleVariantSelection(e) {
        const checkbox = e.target;
        const variant = checkbox.dataset.variant;
        let allVariants = [];
         if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariants = this.fullReport.variantCoverages.map(v => v.name);
        }

        if (variant === 'all') {
            this.state.filters.variants = checkbox.checked ? allVariants : [];
            this.elements.variantFilterList.querySelectorAll('.variant-toggle').forEach(cb => {
                cb.checked = checkbox.checked;
            });
        } else {
            if (checkbox.checked) {
                this.state.filters.variants.push(variant);
            } else {
                this.state.filters.variants = this.state.filters.variants.filter(v => v !== variant);
            }

            const allCheckbox = this.elements.variantFilterList.querySelector('[data-variant="all"]');
            if(allCheckbox) allCheckbox.checked = this.state.filters.variants.length === allVariants.length;
        }

        this.updateVariantButtonText();
        this.render();
    },

    updateFilterButtons() {
        this.elements.moduleFilterText.textContent = this.state.filters.module === 'all'
            ? 'All'
            : this.state.filters.module;

        this.elements.packageFilterText.textContent = this.state.filters.package === 'all'
            ? 'All'
            : this.state.filters.package;

        this.elements.classFilterText.textContent = this.state.filters.class === 'all'
            ? 'All'
            : this.state.filters.class;

        this.elements.testSuiteFilterText.textContent = this.state.filters.testSuite === 'Aggregated'
            ? 'All'
            : this.state.filters.testSuite;
    },

    updateVariantButtonText() {
        const selectedCount = this.state.filters.variants.length;
        let allVariantsCount = 0;
         if (this.fullReport.variantCoverages && this.fullReport.variantCoverages.length > 0) {
            allVariantsCount = this.fullReport.variantCoverages.length;
        }

        if (selectedCount === allVariantsCount && allVariantsCount > 0) {
            this.elements.variantFilterText.textContent = 'All';
        } else if (selectedCount === 1) {
            this.elements.variantFilterText.textContent = this.state.filters.variants[0];
        } else {
            this.elements.variantFilterText.textContent = `${selectedCount} Variants`;
        }
    },

    renderBreadcrumbs() {
        if(this.state.viewMode !== 'flat') {
            this.elements.flatBreadcrumbs.innerHTML = '';
            return;
        }
        const { selectedModule, selectedPackage } = this.state; let html = '';
        // "Project" is the root link
        if(selectedModule) {
            html += `<a href="#" class="breadcrumb-link" data-action="go-to-modules">Project</a>`;
        } else {
            html += `<span class="breadcrumb-current">Project</span>`;
        }
        // Module level
        if(selectedModule) {
            html += `<span class="breadcrumb-separator">/</span>`;
            if(selectedPackage) {
                html += `<a href="#" class="breadcrumb-link" data-action="go-to-packages">${selectedModule}</a>`;
            } else {
                html += `<span class="breadcrumb-current">${selectedModule}</span>`;
            }
        }
        // Package level
        if(selectedPackage) {
            html += `<span class="breadcrumb-separator">/</span>`;
            html += `<span class="breadcrumb-current">${selectedPackage}</span>`;
        }
        this.elements.flatBreadcrumbs.innerHTML = html;
    },

    handleBreadcrumbClick(e) {
        const link = e.target.closest('a[data-action]');
        if(!link) return;
        e.preventDefault();
        const action = link.dataset.action;
        switch(action) {
            case 'go-to-modules':
                this.state.currentView = 'modules';
                this.resetSelection();
                this.render();
                break;
            case 'go-to-packages':
                this.state.currentView = 'packages';
                this.state.selectedPackage = null;
                this.render();
                break;
        }
    },

    handleHeaderClick(e) {
        const th = e.target.closest('[data-sort-by]');
        if (!th) return;

        const newSortBy = th.dataset.sortBy;
        if (this.state.sort.by === newSortBy) {
            this.state.sort.order = this.state.sort.order === 'asc' ? 'desc' : 'asc';
        } else {
            this.state.sort.by = newSortBy;
            this.state.sort.order = 'asc';
        }
        this.render();
    },

    handleFilterSelection(filterType, e) {
        e.preventDefault();
        const target = e.target.closest('a');
        if(!target) return;

        const { value } = target.dataset;

        switch (filterType) {
            case 'module':
                this.state.filters.module = value;
                this.state.filters.package = 'all';
                this.state.filters.class = 'all';
                break;
            case 'package':
                this.state.filters.package = value;
                this.state.filters.class = 'all';
                break;
            case 'class':
                this.state.filters.class = value;
                break;
            case 'testSuite':
                 this.state.filters.testSuite = value;
                 break;
            default:
                break;
        }

        this.updateFilterButtons();

        const dropdownElementKey = `${filterType}FilterDropdown`;
        if(this.elements[dropdownElementKey]) {
            this.elements[dropdownElementKey].classList.add('hidden');
        }

        this.render();
    },

    closeDropdownOnClickOutside() {
        const dropdownConfigs = [
            { btn: this.elements.moduleFilterBtn, dropdown: this.elements.moduleFilterDropdown },
            { btn: this.elements.testSuiteFilterBtn, dropdown: this.elements.testSuiteFilterDropdown },
            { btn: this.elements.packageFilterBtn, dropdown: this.elements.packageFilterDropdown },
            { btn: this.elements.classFilterBtn, dropdown: this.elements.classFilterDropdown },
            { btn: this.elements.variantFilterBtn, dropdown: this.elements.variantFilterDropdown },
            { btn: this.elements.viewModeBtn, dropdown: this.elements.viewModeDropdown }
        ];

        document.addEventListener('click', (event) => {
            dropdownConfigs.forEach(({ btn, dropdown }) => {
                if (!btn.contains(event.target) && !dropdown.contains(event.target)) {
                    dropdown.classList.add('hidden');
                }
            });
        });
    },

    handleViewToggle(e) {
        const button = e.target.closest('.view-toggle');
        if (!button) return;
        this.state.currentView = button.dataset.view;
        this.resetSelection();
        this.render();
    },

    handleViewModeChange(e) {
        e.preventDefault();
        const target = e.target.closest('a');
        if(!target) return;

        this.state.viewMode = target.dataset.value;
        this.elements.viewModeText.textContent = target.textContent;
        this.elements.viewModeDropdown.classList.add('hidden');
        this.elements.viewToggles.style.display = this.state.viewMode === 'flat' ? 'block' : 'none';
        this.resetSelection();
        this.render();
    },

    handleRowClick(e) {
        if (this.state.viewMode === 'flat') {
            const td = e.target.closest('td[data-name]');
            if (td) this.handleFlatRowClick(td);
        } else {
            this.handleTreeRowClick(e.target);
        }
    },

    handleFlatRowClick(td) {
        const { name, type, moduleName } = td.dataset;
        if (type === 'module') {
            this.state.selectedModule = name;
            this.state.currentView = 'packages';
        } else if (type === 'package') {
            this.state.selectedModule = moduleName;
            this.state.selectedPackage = name;
            this.state.currentView = 'classes';
        }
        this.render();
    },

    handleTreeRowClick(target) {
        const row = target.closest('tr');
        const arrow = row?.querySelector('.collapsible-arrow:not(.invisible)');
        if (!arrow) return;

        arrow.classList.toggle('open');
        const children = document.querySelectorAll(`[data-parent-id="${row.dataset.id}"]`);
        if (arrow.classList.contains('open')) {
            children.forEach(child => child.classList.remove('hidden'));
        } else {
            this.collapseDescendants(row);
        }
    },

    collapseDescendants(parentRow) {
        document.querySelectorAll(`[data-parent-id="${parentRow.dataset.id}"]`).forEach(child => {
            child.classList.add('hidden');
            const childArrow = child.querySelector('.collapsible-arrow.open');
            if (childArrow) {
                childArrow.classList.remove('open');
                this.collapseDescendants(child);
            }
        });
    },

    resetSelection() {
        this.state.selectedModule = null;
        this.state.selectedPackage = null;
    },

    getSortedData(data) {
        const { by, order } = this.state.sort;
        if (!by) return data;

        return [...data].sort((a, b) => {
            let valueA, valueB;

            if (by === 'name') {
                valueA = a.name;
                valueB = b.name;
            } else {
                const parts = by.split('.');
                const type = parts[0];
                const variant = parts[1];
                const field = parts[2];

                const va = a.variantCoverages ? a.variantCoverages.find(v => v.name === variant) : null;
                const vb = b.variantCoverages ? b.variantCoverages.find(v => v.name === variant) : null;

                valueA = va ? va[type][field] : 0;
                valueB = vb ? vb[type][field] : 0;
            }

            if (typeof valueA === 'string') {
                return order === 'asc' ? valueA.localeCompare(valueB) : valueB.localeCompare(valueA);
            }
            const numA = valueA || 0;
            const numB = valueB || 0;
            return order === 'asc' ? numA - numB : numB - numA;
        });
    },

    render() {
        this.updateDynamicFilters();
        this.updateActiveTabs();
        this.renderBreadcrumbs();
        let dataToRender = this.getFilteredData();
        dataToRender = this.getSortedData(dataToRender);

        this.updateHeaderStats(dataToRender);
        this.renderTable(dataToRender);

        this.updateTooltipsForOverflow();
    },

    updateActiveTabs() {
        document.querySelectorAll('.view-toggle').forEach(btn => {
            btn.classList.remove('active');
        });
        const activeButton = this.elements.flatViewControls.querySelector(`[data-view="${this.state.currentView}"]`);
        if (activeButton) {
            activeButton.classList.add('active');
        }
    },

    filterHierarchicalData(modules, term) {
        if (!term) return modules;

        term = term.toLowerCase();

        return modules.map(module => {
            if (module.name.toLowerCase().includes(term)) {
                return { ...module };
            }
            const filteredPackages = (module.packages || []).map(pkg => {
                if (pkg.name.toLowerCase().includes(term)) {
                    return { ...pkg };
                }

                const filteredClasses = (pkg.classes || []).filter(cls =>
                    cls.name.toLowerCase().includes(term)
                );

                if (filteredClasses.length > 0) {
                    return { ...pkg, classes: filteredClasses };
                }

                return null;
            }).filter(Boolean);

            if (filteredPackages.length > 0) {
                return { ...module, packages: filteredPackages };
            }

            return null;
        }).filter(Boolean);
    },

    getFilteredData() {
        const { viewMode, currentView, selectedModule, selectedPackage, filters } = this.state;

        const getEffectiveCoverage = (item) => {
            if (!item.testSuiteCoverages) return [];

            const suite = item.testSuiteCoverages.find(ts => ts.name === filters.testSuite);
            // If a suite is not found for a given item (e.g., a test didn't cover this class),
            // return an empty array.
            return suite ? suite.variantCoverages : [];
        };

        const addEffectiveCoverage = (item, type, context = {}) => {
            const newItem = { ...item, type, ...context, testSuiteName: this.state.filters.testSuite  };
            newItem.variantCoverages = getEffectiveCoverage(item);
            return newItem;
        };

        let hierarchicalData = this.fullReport.modules.map(m => {
            const moduleWithCoverage = addEffectiveCoverage(m, 'module');
            moduleWithCoverage.packages = (m.packages || []).map(p => {
                const pkgWithCoverage = addEffectiveCoverage(p, 'package');
                pkgWithCoverage.classes = (p.classes || []).map(c => addEffectiveCoverage(c, 'class'));
                return pkgWithCoverage;
            });
            return moduleWithCoverage;
        });

        if (filters.search) {
            hierarchicalData = this.filterHierarchicalData(hierarchicalData, filters.search);
        }

        if (filters.module !== 'all') {
            hierarchicalData = hierarchicalData.filter(m => m.name === filters.module);
        }
        if (filters.package !== 'all') {
            hierarchicalData = hierarchicalData.map(m => ({
                ...m,
                packages: m.packages.filter(p => p.name === filters.package)
            })).filter(m => m.packages.length > 0);
        }
        if (filters.class !== 'all') {
            hierarchicalData = hierarchicalData.map(m => ({
                ...m,
                packages: m.packages.map(p => ({
                    ...p,
                    classes: p.classes.filter(c => c.name === filters.class)
                })).filter(p => p.classes.length > 0)
            })).filter(m => m.packages.length > 0);
        }
        if (viewMode === 'tree') {
            return hierarchicalData;
        }

        let flatData;

        if (selectedPackage) {
            const module = hierarchicalData.find(m => m.name === selectedModule);
            const pkg = module?.packages.find(p => p.name === selectedPackage);
            flatData = (pkg?.classes || []).map(c => addEffectiveCoverage(c, 'class', { packageName: pkg.name, moduleName: module.name }));
        } else if (selectedModule) {
            const module = hierarchicalData.find(m => m.name === selectedModule);
            flatData = (module?.packages || []).map(p => addEffectiveCoverage(p, 'package', { moduleName: module.name }));
        } else {
            if (currentView === 'packages') {
                flatData = hierarchicalData.flatMap(m =>
                    (m.packages || []).map(p => addEffectiveCoverage(p, 'package', { moduleName: m.name }))
                );
            } else if (currentView === 'classes') {
                flatData = hierarchicalData.flatMap(m =>
                    (m.packages || []).flatMap(p =>
                        (p.classes || []).map(c => addEffectiveCoverage(c, 'class', { packageName: p.name, moduleName: m.name }))
                    )
                );
            } else {
                flatData = hierarchicalData;
            }
        }

        return flatData;
    },

    updateHeaderStats(dataToRender) {
        const { viewMode, currentView } = this.state;
        let relevantClasses;
        let moduleCount = 0;

        if(viewMode === 'tree'){
            relevantClasses = dataToRender.flatMap(m => (m.packages || []).flatMap(p => p.classes || []));
            moduleCount = dataToRender.length;
        } else {
            if (currentView === 'classes') {
                relevantClasses = dataToRender;
                moduleCount = new Set(dataToRender.map(c => c.moduleName)).size;
            } else if (currentView === 'packages') {
                relevantClasses = dataToRender.flatMap(p => p.classes || []);
                moduleCount = new Set(dataToRender.map(p => p.moduleName)).size;
            } else {
                relevantClasses = dataToRender.flatMap(m => (m.packages || []).flatMap(p => p.classes || []));
                moduleCount = dataToRender.length;
            }
        }

        this.elements.totalModules.textContent = moduleCount;
        this.elements.totalClasses.textContent = relevantClasses.length;
    },

    updateDynamicFilters() {
        const { selectedModule, selectedPackage } = this.state;
        let contextModules = this.fullReport.modules;

        if (selectedModule) {
            contextModules = contextModules.filter(m => m.name === selectedModule);
        }

        let contextPackages = contextModules.flatMap(m => m.packages || []);
        if (selectedPackage) {
            contextPackages = contextPackages.filter(p => p.name === selectedPackage);
        }

        const moduleOptions = [
            { name: 'All', value: 'all' },
            ...contextModules.map(m => ({ name: m.name, value: m.name }))
        ];

        const testSuiteOptions = [
            { name: 'All', value: 'Aggregated' },
            ...[...new Set(contextModules.flatMap(m => (m.testSuiteCoverages || []).map(ts => ts.name))
                .filter(name => name !== 'Aggregated'))]
                .sort()
                .map(name => ({ name, value: name }))
        ];

        const packageOptions = [
            { name: 'All', value: 'all' },
            ...[...new Set(contextPackages.map(p => p.name))]
                .sort()
                .map(name => ({ name, value: name }))
        ];

        const classOptions = [
            { name: 'All', value: 'all' },
            ...[...new Set(contextPackages.flatMap(p => p.classes || []).map(c => c.name))]
                .sort()
                .map(name => ({ name, value: name }))
        ];

        this.elements.moduleFilterList.innerHTML = moduleOptions.map(opt =>
            `<a href="#" data-value="${opt.value}" class="dropdown-item">${opt.name}</a>`
        ).join('');

        this.elements.testSuiteFilterList.innerHTML = testSuiteOptions.map(opt =>
            `<a href="#" data-value="${opt.value}" class="dropdown-item">${opt.name}</a>`
        ).join('');

        this.elements.packageFilterList.innerHTML = packageOptions.map(opt =>
            `<a href="#" data-value="${opt.value}" class="dropdown-item">${opt.name}</a>`
        ).join('');

        this.elements.classFilterList.innerHTML = classOptions.map(opt =>
            `<a href="#" data-value="${opt.value}" class="dropdown-item">${opt.name}</a>`
        ).join('');
    },

    renderTable(dataToRender) {
        this.renderHeaders();
        if (this.state.viewMode === 'tree') {
            this.renderTreeRows(dataToRender);
        } else {
            this.renderFlatRows(dataToRender);
        }
    },

    updateTooltipsForOverflow() {
        const cells = this.elements.coverageData.querySelectorAll('.sticky-name');

        cells.forEach(cell => {
            const isOverflowing = cell.scrollWidth > cell.clientWidth;

            if (!isOverflowing) {
                cell.removeAttribute('title');
            }
        });
    },

    getCoverageClass(percentage) {
        if (percentage === '--') return 'text-gray-500';
        if (percentage >= 80) return 'text-green-600';
        if (percentage >= 60) return 'text-yellow-600';
        return 'text-red-600';
    },

    getCoverageValues(item, variantName) {
        const found = item.variantCoverages ? item.variantCoverages.find(v => v.name === variantName) : null;

        if (found) {
            return {
                instrPercent: found.instruction.percent + '%',
                instrRatio: `${found.instruction.covered}/${found.instruction.total}`,
                branchPercent: found.branch.percent + '%',
                branchRatio: `${found.branch.covered}/${found.branch.total}`,
                instrColor: this.getCoverageClass(found.instruction.percent),
                branchColor: this.getCoverageClass(found.branch.percent)
            };
        }

        return {
            instrPercent: '--',
            instrRatio: '0/0',
            branchPercent: '--',
            branchRatio: '0/0',
            instrColor: this.getCoverageClass('--'),
            branchColor: this.getCoverageClass('--')
        };
    },

    renderHeaders() {
        const { viewMode, currentView, filters, sort } = this.state;
        const topHeader = document.createElement('tr');
        topHeader.className = "border-b border-gray-200";
        const subHeader = document.createElement('tr');
        subHeader.className = "border-b border-gray-200";

        const mainHeaderTitle = viewMode === 'tree' ? 'Module' : currentView.charAt(0).toUpperCase() + currentView.slice(1);
        const firstColClass = "py-4 px-6 text-left font-semibold text-gray-700 sticky-name bg-gray-50 z-30";
        const sortIndicator = (key) => sort.by === key ? (sort.order === 'asc' ? '▲' : '▼') : '';
        topHeader.innerHTML = `<th class="${firstColClass}" data-sort-by="name">${mainHeaderTitle} ${sortIndicator('name')}</th>`;
        subHeader.innerHTML = `<th class="py-2 px-6 sticky-name bg-gray-50 z-30"></th>`;

        if (viewMode === 'flat' && (currentView === 'packages' || currentView === 'classes')) {
            const contextTitle = (currentView === 'packages') ? 'Module' : 'Package';
            topHeader.innerHTML += `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50"></th>`;
            subHeader.innerHTML += `<th class="py-2 px-4 text-left text-xs font-medium text-gray-600">${contextTitle}</th>`;

            if (currentView === 'classes') {
                topHeader.innerHTML += `<th class="py-4 px-6 text-left font-semibold text-gray-700 bg-gray-50"></th>`;
                subHeader.innerHTML += `<th class="py-2 px-4 text-left text-xs font-medium text-gray-600">Module</th>`;
            }
        }

        const variants = [...filters.variants].sort();

        variants.forEach(v => {
            const vals = this.getCoverageValues(this.fullReport, v);

            topHeader.innerHTML += `<th colspan="2" class="py-4 px-4 text-center font-semibold text-gray-700 border-l border-gray-200">
                <div class="flex flex-col"><span>${v}</span><span class="text-sm font-bold ${vals.instrColor} mt-1">${vals.instrPercent}</span></div>
            </th>`;

            const instrKey = `instruction.${v}.percent`;
            const branchKey = `branch.${v}.percent`;
            subHeader.innerHTML += `<th class="py-2 px-4 text-center text-xs font-medium text-gray-600 border-l border-gray-200" data-sort-by="${instrKey}">Instruction ${sortIndicator(instrKey)}</th>
                                    <th class="py-2 px-4 text-center text-xs font-medium text-gray-600" data-sort-by="${branchKey}">Branch ${sortIndicator(branchKey)}</th>`;
        });

        this.elements.tableHeaders.innerHTML = '';
        this.elements.tableHeaders.appendChild(topHeader);
        this.elements.tableHeaders.appendChild(subHeader);
    },

    renderTreeRows(modules) {
        const isSearching = !!this.state.filters.search;

        const renderRow = (item, level, type, parentId = '', context = {}) => {
            const hasChildren = type !== 'class' && (
                (item.packages && item.packages.length > 0) ||
                (item.classes && item.classes.length > 0)
            );

            let nameContent;
            if (type === 'class') {
                nameContent = `<a href="#" class="font-medium text-blue-700 hover:underline class-link" data-class-name="${item.name}" data-module-name="${context.moduleName}" data-package-name="${context.packageName}" data-test-suite-name="${context.testSuiteName || ''}">${item.name}</a>`;
            } else {
                nameContent = `<span class="font-medium text-gray-900">${item.name}</span>`;
            }

            const chevron = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="collapsible-arrow w-4 h-4 text-gray-600 ${!hasChildren ? 'invisible' : ''} ${isSearching ? 'open' : ''}"><path d="m9 18 6-6-6-6"></path></svg>`;
            const coverageCells = [...this.state.filters.variants].sort().map(v => {
                const vals = this.getCoverageValues(item, v);
                return `
                <td class="py-3 px-4 text-center"><div class="flex flex-col"><span class="font-bold ${vals.instrColor}">${vals.instrPercent}</span><span class="text-xs text-gray-500">${vals.instrRatio}</span></div></td>
                <td class="py-3 px-4 text-center"><div class="flex flex-col"><span class="font-bold ${vals.branchColor}">${vals.branchPercent}</span><span class="text-xs text-gray-500">${vals.branchRatio}</span></div></td>
            `}).join('');

            const rowClasses = `table-row border-b border-gray-200 hover:bg-gray-50 ${level > 0 && !isSearching ? 'child-row hidden' : 'child-row'}`;

            return `<tr class="${rowClasses}" data-id="${item.name}" data-parent-id="${parentId}">
                <td class="py-3 px-6 sticky-name" title="${item.name}"><div class="flex items-center gap-2 cursor-pointer" style="padding-left: ${level * 1.5}rem;">${chevron}${nameContent}</div></td>
                ${coverageCells}
            </tr>`;
        };

        let html = modules.map(module => {
            let moduleContext = { moduleName: module.name };
            let childrenHtml = (module.packages || []).map(pkg => {
                let packageContext = { ...moduleContext, packageName: pkg.name };
                return renderRow(pkg, 1, 'package', module.name, packageContext) +
                    (pkg.classes || []).map(cls => renderRow(cls, 2, 'class', pkg.name, packageContext)).join('');
            }).join('');

            return renderRow(module, 0, 'module', '', moduleContext) + childrenHtml;
        }).join('');
        this.elements.coverageData.innerHTML = html;
    },

    renderFlatRows(data) {
        if (!data.length) {
            this.elements.coverageData.innerHTML = `<tr><td colspan="10" class="text-center py-8 text-gray-500">No results found.</td></tr>`;
            return;
        }
        this.elements.coverageData.innerHTML = data.map(item => {
            const variants = [...this.state.filters.variants].sort();
            const coverageCells = variants.map(v => {
                const vals = this.getCoverageValues(item, v);
                return `
                <td class="py-3 px-4 text-center"><div class="flex flex-col"><span class="font-bold ${vals.instrColor}">${vals.instrPercent}</span><span class="text-xs text-gray-500">${vals.instrRatio}</span></div></td>
                <td class="py-3 px-4 text-center"><div class="flex flex-col"><span class="font-bold ${vals.branchColor}">${vals.branchPercent}</span><span class="text-xs text-gray-500">${vals.branchRatio}</span></div></td>
            `}).join('');

            let nameCell;
            switch (this.state.currentView) {
                case 'packages':
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" title="${item.name}" data-name="${item.name}" data-type="${item.type}" data-module-name="${item.moduleName}">${item.name}</td><td class="py-3 px-6">${item.moduleName}</td>`;
                    break;
                case 'classes':
                    nameCell = `<td class="py-3 px-6 sticky-name" title="${item.name}"><a href="#" class="font-medium text-blue-700 hover:underline class-link" data-class-name="${item.name}" data-module-name="${item.moduleName}" data-package-name="${item.packageName}">${item.name}</a></td><td class="py-3 px-6">${item.packageName}</td>`;
                    nameCell += `<td class="py-3 px-6">${item.moduleName || ''}</td>`;
                    break;
                default: // modules
                    nameCell = `<td class="py-3 px-6 sticky-name font-medium text-blue-700 hover:underline cursor-pointer" title="${item.name}" data-name="${item.name}" data-type="${item.type}" data-module-name="${item.name}">${item.name}</td>`;
            }
            return `<tr class="table-row border-b border-gray-200 hover:bg-gray-50">${nameCell}${coverageCells}</tr>`;
        }).join('');
    }
};

document.addEventListener('DOMContentLoaded', () => App.init());
