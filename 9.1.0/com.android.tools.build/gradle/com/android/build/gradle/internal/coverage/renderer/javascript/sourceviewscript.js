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

const SourceViewApp = {
    elements: {},
    classData: null,
    context: {},
    state: {
        selectedVariants: [],
        isLoading: false
    },

    init() {
        this.cacheElements();
        this.bindEvents();
        this.closeDropdownOnClickOutside();
    },

    cacheElements() {
        this.elements = {
            functionListContainer: document.querySelector('.func-list-container'),
            functionList: document.getElementById('function-list'),
            functionSearch: document.getElementById('function-search'),
            functionSearchClearBtn: document.getElementById('function-search-clear-btn'),
            sourceBreadcrumbs: document.getElementById('source-breadcrumbs'),
            variantFilterBtn: document.getElementById('source-variant-filter-btn'),
            sourceVariantFilterText: document.getElementById('source-variant-filter-text'),
            variantFiltersDropdown: document.getElementById('source-variant-filters-dropdown'),
            variantFilters: document.getElementById('source-variant-filters'),
            sourceViewContainer: document.getElementById('source-view-container'),
            loadingOverlay: this.createLoadingOverlay()
        };
    },

    createLoadingOverlay() {
        const div = document.createElement('div');
        div.className = 'fixed inset-0 bg-white bg-opacity-75 flex items-center justify-center z-50 hidden';
        div.innerHTML = `
            <div class="flex flex-col items-center">
                <svg class="animate-spin h-10 w-10 text-blue-600 mb-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span class="text-lg font-semibold text-gray-700">Loading Source...</span>
            </div>
        `;
        document.body.appendChild(div);
        return div;
    },

    /**
     * Entry point to load specific source files for a class and then render.
     */
    async loadAndRender(classData, context = {}) {
        this.classData = classData;
        this.context = context;
        this.toggleLoading(true);

        const requiredPaths = [...new Set(classData.variantSourceFilePaths.map(v => v.path))];

        try {
            await this.fetchSourceFiles(requiredPaths);

            this.render();
        } catch (error) {
            console.error("[SourceView] Failed to load source files:", error);
            this.elements.sourceViewContainer.innerHTML = `
                <div class="p-8 text-center text-red-600">
                    <h3 class="text-lg font-bold">Error Loading Source</h3>
                    <p>Could not load coverage data for ${classData.sourceFileName}.</p>
                </div>`;
        } finally {
            this.toggleLoading(false);
        }
    },

    toggleLoading(show) {
        this.state.isLoading = show;
        if (show) {
            this.elements.loadingOverlay.classList.remove('hidden');
        } else {
            this.elements.loadingOverlay.classList.add('hidden');
        }
    },

    /**
     * Dynamically injects script tags.
     */
    fetchSourceFiles(paths) {
        const promises = paths.map(path => {
            if (window.coverageData && window.coverageData[path]) {
                return Promise.resolve();
            }

            return new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.src = `sourcefiles/${path}.json.js`;
                script.onload = () => resolve();
                script.onerror = () => reject(new Error(`Failed to load ${script.src}`));
                document.body.appendChild(script);
            });
        });

        return Promise.all(promises);
    },

    render() {
        const availableVariants = [...new Set(this.classData.variantSourceFilePaths.map(v => v.variantName))];
        this.state.selectedVariants = [...availableVariants];

        this.renderBreadcrumbs();
        this.renderFilters(availableVariants);
        this.renderFunctionList();
        this.renderAllVariantViews();
        this.updateVariantButtonText();
    },

    renderBreadcrumbs() {
        const { packageName, sourceFileName } = this.classData;
        const { moduleName, testSuiteName } = this.context;

        let html = `
            <a href="#" class="action-btn" data-action="go-back">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
                </svg>
                <span>Back</span>
            </a>`;

        html += `<div class="flex items-center gap-2 text-sm">
            <span class="breadcrumb-separator">/</span>
            <a href="#" class="breadcrumb-link" data-action="go-to-modules">Project</a>`;

        if (moduleName) {
            html += `
                <span class="breadcrumb-separator">/</span>
                <a href="#" class="breadcrumb-link" data-action="go-to-packages" data-module-name="${moduleName}">${moduleName}</a>`;
        }
        if (packageName) {
             html += `
                <span class="breadcrumb-separator">/</span>
                <a href="#" class="breadcrumb-link" data-action="go-to-classes" data-module-name="${moduleName}" data-package-name="${packageName}">${packageName}</a>`;
        }
        if (testSuiteName !== "Aggregated") {
             html += `
                <span class="breadcrumb-separator">/</span>
                <span class="text-gray-500">${testSuiteName}</span>`;
        }

        html += `
            <span class="breadcrumb-separator">/</span>
            <span class="font-semibold text-gray-800">${sourceFileName}</span>
        </div>`;

        this.elements.sourceBreadcrumbs.innerHTML = html;
    },

    bindEvents() {
        this.elements.variantFilterBtn.addEventListener('click', this.toggleVariantDropdown.bind(this));
        this.elements.variantFilters.addEventListener('change', this.handleVariantToggle.bind(this));
        this.elements.functionSearch.addEventListener('input', this.handleFunctionSearch.bind(this));
        this.elements.functionSearchClearBtn.addEventListener('click', this.handleFunctionSearchClear.bind(this));
        this.elements.functionList.addEventListener('click', this.handleMethodClick.bind(this));
        this.elements.sourceBreadcrumbs.addEventListener('click', this.handleBreadcrumbClick.bind(this));
    },

    handleBreadcrumbClick(e) {
        const link = e.target.closest('a[data-action]');
        if (!link) return;

        e.preventDefault();
        const { action, moduleName, packageName } = link.dataset;

        switch (action) {
            case 'go-back':
                App.showReportView();
                break;

            case 'go-to-modules':
                CoverageReportApp.resetSelection();
                CoverageReportApp.state.currentView = 'modules';
                App.showReportView();
                CoverageReportApp.render();
                break;
            case 'go-to-packages':
                CoverageReportApp.state.selectedModule = moduleName;
                CoverageReportApp.state.selectedPackage = null;
                CoverageReportApp.state.currentView = 'packages';
                App.showReportView();
                CoverageReportApp.render();
                break;
            case 'go-to-classes':
                CoverageReportApp.state.selectedModule = moduleName;
                CoverageReportApp.state.selectedPackage = packageName;
                CoverageReportApp.state.currentView = 'classes';
                App.showReportView();
                CoverageReportApp.render();
                break;
        }
    },

    toggleVariantDropdown() {
        this.elements.variantFiltersDropdown.classList.toggle('hidden');
    },

    closeDropdownOnClickOutside() {
        document.addEventListener('click', (event) => {
            if (!this.elements.variantFilterBtn.contains(event.target) && !this.elements.variantFiltersDropdown.contains(event.target)) {
                this.elements.variantFiltersDropdown.classList.add('hidden');
            }
        });
    },

    handleVariantToggle(e) {
        const checkbox = e.target;
        const variant = checkbox.dataset.variant;
        const available = [...new Set(this.classData.variantSourceFilePaths.map(v => v.variantName))];

        if (variant === 'all') {
            this.state.selectedVariants = checkbox.checked ? available : [];
            this.elements.variantFilters.querySelectorAll('.variant-toggle').forEach(cb => {
                cb.checked = checkbox.checked;
            });
        } else {
            if (checkbox.checked) {
                this.state.selectedVariants.push(variant);
            } else {
                this.state.selectedVariants = this.state.selectedVariants.filter(v => v !== variant);
            }

            const allCheckbox = this.elements.variantFilters.querySelector('[data-variant="all"]');
            if(allCheckbox) allCheckbox.checked = this.state.selectedVariants.length === available.length;
        }

        this.renderAllVariantViews();
        this.updateVariantButtonText();
    },

    updateVariantButtonText() {
        const selectedCount = this.state.selectedVariants.length;
        const availableVariants = [...new Set(this.classData.variantSourceFilePaths.map(v => v.variantName))];
        const allVariantsCount = availableVariants.length;

        if (selectedCount === allVariantsCount && allVariantsCount > 0) {
            this.elements.sourceVariantFilterText.textContent = 'All';
        } else if (selectedCount === 1) {
            this.elements.sourceVariantFilterText.textContent = this.state.selectedVariants[0];
        } else {
            this.elements.sourceVariantFilterText.textContent = `${selectedCount} Variants`;
        }
    },

    handleFunctionSearch(e) {
        const searchTerm = e.target.value.toLowerCase();
        this.elements.functionList.querySelectorAll('.method-link').forEach(link => {
            const methodName = link.textContent.trim().toLowerCase();
            link.style.display = methodName.includes(searchTerm) ? 'block' : 'none';
        });

        if (searchTerm.length > 0) {
            this.elements.functionSearchClearBtn.classList.remove('hidden');
        } else {
            this.elements.functionSearchClearBtn.classList.add('hidden');
        }
    },

    handleFunctionSearchClear() {
        this.elements.functionSearch.value = '';
        this.handleFunctionSearch({ target: this.elements.functionSearch });
        this.elements.functionSearch.focus();
    },

    handleMethodClick(e) {
        const methodLink = e.target.closest('.method-link');
        if (!methodLink) return;

        this.elements.functionList.querySelectorAll('.method-link').forEach(el => el.classList.remove('bg-gray-200'));
        methodLink.classList.add('bg-gray-200');

        const methodName = methodLink.dataset.methodName;
        const methodData = this.classData.methods.find(m => m.name === methodName);

        if (methodData && methodData.variantLineNumbers) {
            methodData.variantLineNumbers.forEach(mapping => {
                if (this.state.selectedVariants.includes(mapping.variantName)) {
                    const variantContainer = this.elements.sourceViewContainer.querySelector(`.variant-code-view[data-variant="${mapping.variantName}"]`);
                    if (variantContainer) {
                        const codeContainer = variantContainer.querySelector('.code-container');
                        const row = variantContainer.querySelector(`tr[data-line-number="${mapping.lineNumber}"]`);
                        if (row && codeContainer) {
                            const newScrollTop = row.offsetTop - (codeContainer.clientHeight / 2) + (row.clientHeight / 2);
                            codeContainer.scrollTo({
                                top: newScrollTop,
                                behavior: 'smooth'
                            });

                            row.classList.remove('highlight-blink');
                            void row.offsetWidth; // Trigger reflow
                            row.classList.add('highlight-blink');
                            setTimeout(() => row.classList.remove('highlight-blink'), 1500);
                        }
                    }
                }
            });
        }
    },

    renderFilters(availableVariants) {
        const variantOptions = [{name: 'All', value: 'all'}, ...availableVariants.map(v => ({name: v, value: v}))];

        this.elements.variantFilters.innerHTML = variantOptions.map(opt => `
            <label class="flex items-center gap-2 cursor-pointer px-2 py-1 hover:bg-gray-100 rounded">
                <input type="checkbox" class="variant-toggle w-4 h-4 text-blue-600 rounded focus:ring-2 focus:ring-blue-500" data-variant="${opt.value}" ${this.state.selectedVariants.includes(opt.value) || (this.state.selectedVariants.length === availableVariants.length && opt.value === 'all') ? 'checked' : ''} />
                <span class="text-sm text-gray-700">${opt.name}</span>
            </label>
        `).join('');
    },

    renderFunctionList() {
        const header = this.elements.functionListContainer.querySelector('h3');
        if(header) header.textContent = "Methods";

        if (!this.classData.methods || this.classData.methods.length === 0) {
            this.elements.functionList.innerHTML = '<div class="text-sm text-gray-500 px-3">No methods found</div>';
            return;
        }

        this.elements.functionList.innerHTML = this.classData.methods.map(method => `
            <div class="method-link w-full text-left px-3 py-2 rounded-lg transition-colors hover:bg-gray-100 text-gray-700 block cursor-pointer" data-method-name="${method.name}">
                <div class="text-sm font-mono truncate">${method.name}</div>
            </div>`
        ).join('');
    },

    renderAllVariantViews() {
        this.elements.sourceViewContainer.innerHTML = this.state.selectedVariants.map(variant => this.renderVariantView(variant)).join('');
    },

    getCoverageClass(percent, covered, total) {
        if (total === 0) return '';
        if (covered === 0 && total > 0) return 'cell-uncovered';
        if (covered === total) return 'cell-covered';
        return 'cell-partial';
    },

    getTextClass(percent) {
        if (percent >=80) return 'text-green-600';
        if (percent >= 60) return 'text-yellow-600';
        return 'text-red-600';
    },

    /**
     * Helper to select the correct coverage object for a given variant group.
     * Prioritizes the active Test Suite (from context). Falls back to Aggregated.
     */
    resolveCoverageForGroup(variantGroup) {
        if (!variantGroup || !variantGroup.testSuiteCoverages) return null;

        const testSuites = variantGroup.testSuiteCoverages;
        const activeSuiteName = this.context.testSuiteName;

        if (activeSuiteName) {
            const specific = testSuites.find(ts => ts.testSuiteName === activeSuiteName);
            if (specific) return specific.variantCoverage;
        }

        const aggregated = testSuites.find(ts => ts.testSuiteName === 'Aggregated');
        if (aggregated) return aggregated.variantCoverage;

        return testSuites[0]?.variantCoverage || null;
    },

    renderVariantView(variantName) {
        const pathObj = this.classData.variantSourceFilePaths.find(v => v.variantName === variantName);
        if (!pathObj) return ``;

        const fileReport = window.coverageData ? window.coverageData[pathObj.path] : null;

        if (!fileReport) {
            return `
                <div class="variant-code-view" data-variant="${variantName}">
                    <div class="variant-header"><div>${variantName}</div></div>
                    <div class="p-4 text-gray-500 text-center">Data not loaded for this variant.</div>
                </div>`;
        }

        const summaryGroup = fileReport.variantCoverageSummary.find(s => s.variantName === variantName);
        const summaryStats = this.resolveCoverageForGroup(summaryGroup);

        const percent = summaryStats ? summaryStats.instruction.percent : 0;
        const covered = summaryStats ? summaryStats.instruction.covered : 0;
        const total = summaryStats ? summaryStats.instruction.total : 0;
        const colorClass = this.getTextClass(percent);

        const tbody = fileReport.linesCoverages.map(line => {
            const { lineNumber, lineText, variantCoverageDetails } = line;

            const vGroup = variantCoverageDetails.find(vg => vg.variantName === variantName);
            const vStats = this.resolveCoverageForGroup(vGroup);

            let statusClass = '';
            let branchIndicator = '';

            if (vStats) {
                statusClass = this.getCoverageClass(
                    vStats.instruction.percent,
                    vStats.instruction.covered,
                    vStats.instruction.total
                );

                if (vStats.branch.total > 0) {
                    const bCovered = vStats.branch.covered;
                    const bTotal = vStats.branch.total;
                    const missed = bTotal - bCovered;
                    const color = bCovered === bTotal ? 'green' : (bCovered > 0 ? 'yellow' : 'red');
                    const tooltipText = `${missed} of ${bTotal} branch${bTotal > 1 ? 'es' : ''} missed`;
                    branchIndicator = `<div class="branch-diamond ${color}"><div class="branch-tooltip">${tooltipText}</div></div>`;
                }
            }

            return `
                <tr data-line-number="${lineNumber}">
                    <td class="line-number">${branchIndicator}${lineNumber}</td>
                    <td class="code-cell ${statusClass}"><pre>${lineText || ' '}</pre></td>
                </tr>
            `;
        }).join('');

        const headerTitle = this.context.testSuiteName
            ? `${variantName} (${this.context.testSuiteName})`
            : variantName;

        return `
            <div class="variant-code-view" data-variant="${variantName}">
                <div class="variant-header">
                    <div>${headerTitle}</div>
                    <div class="flex items-baseline gap-2 mt-1">
                        <span class="font-bold ${colorClass}">${percent}%</span>
                        <span class="text-xs text-gray-500 font-normal">${covered}/${total} Lines</span>
                    </div>
                </div>
                <div class="code-container">
                    <table class="code-table">
                        <tbody>${tbody}</tbody>
                    </table>
                </div>
            </div>
        `;
    }
};
